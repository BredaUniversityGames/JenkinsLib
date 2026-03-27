package com.buas.deploy

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Google Drive deployment.
 * Uploads build artifacts to Google Drive using the Drive v3 resumable upload API.
 * Uses Java standard library for JWT signing and HTTP — no external dependencies.
 *
 * All file I/O runs on the Jenkins agent (via Pipeline steps), not the controller.
 */
class GDrive implements Serializable {
    def steps

    private static final int CHUNK_SIZE = 256 * 1024 * 16 // 4 MB

    GDrive(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        return [
            steps.credentials(name: 'GDRIVE_CREDENTIALS_ID', defaultValue: overrides.GDRIVE_CREDENTIALS_ID ?: prev.GDRIVE_CREDENTIALS_ID ?: '',
                   description: 'GDrive service account file',
                   credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl', required: true),
            steps.string(name: 'GDRIVE_FOLDER_ID', defaultValue: overrides.GDRIVE_FOLDER_ID ?: prev.GDRIVE_FOLDER_ID ?: '',
                   description: 'Google Drive parent folder ID')
        ]
    }

    def execute(Map params, Map ctx) {
        deploy(
            credentialsId:   params.GDRIVE_CREDENTIALS_ID,
            source:          ctx.outputDir,
            folderId:        params.GDRIVE_FOLDER_ID,
            platform:        ctx.buildPlatform ?: 'Win64',
            config:          ctx.buildConfig ?: 'Development'
        )
    }

    def deploy(Map config) {
        def credentialsId = config.credentialsId
        def source = config.source
        def folderId = config.folderId
        def platform = config.platform ?: 'Win64'
        def buildConfig = config.config ?: 'Development'

        if (!credentialsId) { steps.error("GDRIVE_CREDENTIALS_ID is required") }
        if (!folderId)      { steps.error("GDRIVE_FOLDER_ID is required") }

        def platformDir = steps.utilWin.platformOutputDir(platform)
        def buildNum = String.format('%03d', steps.env.BUILD_NUMBER as int)
        def archiveName = "${steps.env.JOB_BASE_NAME}_${platform}_${buildConfig}_${buildNum}"
        def sourceDir = "${source}\\${platformDir}"

        steps.utilZip.pack(sourceDir, archiveName, false)

        def zipPath = "${steps.env.WORKSPACE}\\${archiveName}.zip"

        steps.withCredentials([steps.file(credentialsId: credentialsId, variable: 'GDRIVE_SECRET')]) {
            def authContent = steps.readFile(file: steps.env.GDRIVE_SECRET)
            def token = getAccessToken(authContent)
            def uploadUrl = initResumableUpload(token, "${archiveName}.zip", folderId)
            uploadChunked(uploadUrl, zipPath, token)
        }

        steps.bat(script: "del /f \"${zipPath}\"")
    }

    private String createJwt(String authContent) {
        def authJson = new JsonSlurper().parseText(authContent)
        def iss = authJson.client_email
        def privateKeyPem = authJson.private_key

        long now = System.currentTimeMillis() / 1000 as long
        def header = base64url(JsonOutput.toJson([alg: 'RS256', typ: 'JWT']))
        def claims = base64url(JsonOutput.toJson([
            iss:   iss,
            scope: 'https://www.googleapis.com/auth/drive',
            aud:   'https://oauth2.googleapis.com/token',
            iat:   now,
            exp:   now + 3600
        ]))

        def signingInput = "${header}.${claims}"

        // Parse PEM private key
        def keyContent = privateKeyPem
            .replace('-----BEGIN PRIVATE KEY-----', '')
            .replace('-----END PRIVATE KEY-----', '')
            .replaceAll('\\s', '')
        def keyBytes = Base64.decoder.decode(keyContent)
        def keySpec = new PKCS8EncodedKeySpec(keyBytes)
        def key = KeyFactory.getInstance('RSA').generatePrivate(keySpec)

        def sig = Signature.getInstance('SHA256withRSA')
        sig.initSign(key)
        sig.update(signingInput.getBytes('UTF-8'))
        def signature = base64url(sig.sign())

        return "${signingInput}.${signature}"
    }

    private String getAccessToken(String authContent) {
        def jwt = createJwt(authContent)
        def body = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}"

        def url = new URL('https://oauth2.googleapis.com/token')
        def conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Content-Type', 'application/x-www-form-urlencoded')
        conn.doOutput = true
        def bodyBytes = body.getBytes('UTF-8')
        conn.outputStream.write(bodyBytes)
        conn.outputStream.close()

        if (conn.responseCode != 200) {
            def error = conn.errorStream?.text ?: 'unknown error'
            steps.error("Failed to retrieve OAuth token (HTTP ${conn.responseCode}): ${error}")
        }

        def response = new JsonSlurper().parseText(conn.inputStream.text)
        conn.disconnect()
        return response.access_token
    }

    private String initResumableUpload(String token, String fileName, String folderId) {
        def metadata = JsonOutput.toJson([name: fileName, parents: [folderId]])

        def url = new URL('https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true')
        def conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Authorization', "Bearer ${token}")
        conn.setRequestProperty('Content-Type', 'application/json; charset=UTF-8')
        conn.doOutput = true
        def metadataBytes = metadata.getBytes('UTF-8')
        conn.outputStream.write(metadataBytes)
        conn.outputStream.close()

        if (conn.responseCode != 200) {
            def error = conn.errorStream?.text ?: 'unknown error'
            steps.error("Failed to init resumable upload (HTTP ${conn.responseCode}): ${error}")
        }

        def uploadUrl = conn.getHeaderField('Location')
        conn.disconnect()
        if (!uploadUrl) {
            steps.error("Resumable upload init did not return a Location header")
        }
        return uploadUrl
    }

    /**
     * Chunked resumable upload executed on the agent via PowerShell.
     * Reads the file and sends HTTP requests entirely on the agent,
     * avoiding transfer of large binary data through the controller.
     */
    private void uploadChunked(String uploadUrl, String filePath, String token) {
        def escFilePath = filePath.replace("'", "''")
        def escUploadUrl = uploadUrl.replace("'", "''")
        def escToken = token.replace("'", "''")

        def psScript = "\$ErrorActionPreference = 'Stop'\n" +
            "\$filePath = '${escFilePath}'\n" +
            "\$uploadUrl = '${escUploadUrl}'\n" +
            "\$token = '${escToken}'\n" +
            "\$chunkSize = ${CHUNK_SIZE}\n" +
            '''
$fs = [System.IO.File]::OpenRead($filePath)
$fileSize = $fs.Length
Write-Output "Uploading $filePath ($([Math]::Round($fileSize / 1MB, 1)) MB)"

try {
    [long]$offset = 0
    $buffer = New-Object byte[] $chunkSize

    while ($offset -lt $fileSize) {
        $currentChunkSize = [Math]::Min($chunkSize, $fileSize - $offset)
        $fs.Position = $offset
        [void]$fs.Read($buffer, 0, $currentChunkSize)

        $endByte = $offset + $currentChunkSize - 1

        $request = [System.Net.HttpWebRequest]::Create($uploadUrl)
        $request.Method = 'PUT'
        $request.AllowAutoRedirect = $false
        $request.Headers.Add('Authorization', "Bearer $token")
        $request.ContentLength = $currentChunkSize
        $request.Headers.Add('Content-Range', "bytes $offset-$endByte/$fileSize")
        $request.Timeout = 300000

        $reqStream = $request.GetRequestStream()
        $reqStream.Write($buffer, 0, $currentChunkSize)
        $reqStream.Close()

        $progress = [Math]::Round(($endByte + 1) / $fileSize * 1000) / 10

        $statusCode = 0
        $response = $null
        try {
            $response = $request.GetResponse()
            $statusCode = [int]$response.StatusCode
        } catch [System.Net.WebException] {
            $response = $_.Exception.Response
            if ($response) {
                $statusCode = [int]$response.StatusCode
            } else {
                throw "Upload failed at ${progress}%: $($_.Exception.Message)"
            }
        }

        Write-Output "Progress: ${progress}%"

        if ($statusCode -eq 200 -or $statusCode -eq 201) {
            $response.Close()
            Write-Output 'Upload complete.'
            return
        } elseif ($statusCode -eq 308) {
            $range = $response.Headers['Range']
            $response.Close()
            if ($range) {
                $offset = [long]$range.Split('-')[1] + 1
            } else {
                $offset += $currentChunkSize
            }
        } else {
            $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
            $errorBody = $reader.ReadToEnd()
            $reader.Close()
            $response.Close()
            throw "Upload failed at ${progress}% (HTTP ${statusCode}): $errorBody"
        }
    }
} finally {
    $fs.Close()
}
'''
        steps.powershell(script: psScript)
    }

    private static String base64url(String text) {
        return base64url(text.getBytes('UTF-8'))
    }

    private static String base64url(byte[] data) {
        return Base64.urlEncoder.withoutPadding().encodeToString(data)
    }
}
