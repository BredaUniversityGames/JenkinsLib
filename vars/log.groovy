def call(message)
{
   echo "${message}"
}

def debug(message)
{
   echo "DEBUG: ${message}"
}

def warning(message)
{
   echo "WARNING: ${message}"
}

def error(message)
{
   echo "ERROR: ${message}"
}

def currStage()
{
   echo "${STAGE_NAME}"
}

def file(targetFile)
{
   def content = readFile(file: targetFile)
   echo "Content of ${targetFile}:\n\n${content}"
}