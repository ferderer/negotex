def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log not found at: " + buildLog.absolutePath

def log = buildLog.text
assert log.contains("primitive type") :
    "Expected primitive-type error, not found.\n--- build.log (${log.length()} bytes) ---\n${log}"
