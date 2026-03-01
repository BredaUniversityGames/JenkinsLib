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
            platform:        ctx.buildPlatform ?: 'Win64'
        )
    }

    def deploy(Map config) {
        def credentialsId = config.credentialsId
        def source = config.source
        def folderId = config.folderId
        def platform = config.platform ?: 'Win64'

        if (!credentialsId) { steps.error("GDRIVE_CREDENTIALS_ID is required") }
        if (!folderId)      { steps.error("GDRIVE_FOLDER_ID is required") }

        def platformDir = steps.utilWin.platformOutputDir(platform)
        def archiveName = "${steps.env.JOB_BASE_NAME}_${steps.env.BUILD_NUMBER}"
        def sourceDir = "${source}\\${platformDir}"

        steps.utilZip.pack(sourceDir, archiveName, false)

        def zipPath = "${steps.env.WORKSPACE}\\${archiveName}.zip"

        steps.withCredentials([steps.file(credentialsId: credentialsId, variable: 'GDRIVE_SECRET')]) {
            def authFilePath = steps.env.GDRIVE_SECRET
            def token = getAccessToken(authFilePath)
            def uploadUrl = initResumableUpload(token, "${archiveName}.zip", folderId)
            uploadChunked(uploadUrl, zipPath, token)
        }
    }

    private String createJwt(String authFilePath) {
        def authJson = new JsonSlurper().parseText(new File(authFilePath).text)
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

    private String getAccessToken(String authFilePath) {
        def jwt = createJwt(authFilePath)
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

    private void uploadChunked(String uploadUrl, String filePath, String token) {
        def file = new RandomAccessFile(filePath, 'r')
        def fileSize = file.length()
        long offset = 0

        steps.echo("Uploading ${filePath} (${String.format('%.1f', fileSize / (1024.0 * 1024.0))} MB)")

        try {
            while (offset < fileSize) {
                int chunkSize = (int) Math.min(CHUNK_SIZE, fileSize - offset)
                long endByte = offset + chunkSize - 1

                file.seek(offset)
                def buffer = new byte[chunkSize]
                file.readFully(buffer)

                def url = new URL(uploadUrl)
                def conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = 'PUT'
                conn.setRequestProperty('Authorization', "Bearer ${token}")
                conn.setRequestProperty('Content-Length', String.valueOf(chunkSize))
                conn.setRequestProperty('Content-Range', "bytes ${offset}-${endByte}/${fileSize}")
                conn.doOutput = true
                conn.outputStream.write(buffer)
                conn.outputStream.close()

                def code = conn.responseCode
                def progress = Math.round((endByte + 1) / fileSize * 1000) / 10.0
                steps.echo("Progress: ${progress}%")

                if (code == 200 || code == 201) {
                    steps.echo("Upload complete.")
                    return
                } else if (code == 308) {
                    def range = conn.getHeaderField('Range')
                    if (range) {
                        offset = Long.parseLong(range.split('-')[1]) + 1
                    } else {
                        offset += chunkSize
                    }
                } else {
                    def error = conn.errorStream?.text ?: 'unknown error'
                    steps.error("Upload failed at ${progress}% (HTTP ${code}): ${error}")
                }
            }
        } finally {
            file.close()
        }
    }

    private static String base64url(String text) {
        return base64url(text.getBytes('UTF-8'))
    }

    private static String base64url(byte[] data) {
        return Base64.urlEncoder.withoutPadding().encodeToString(data)
    }
}
