def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log not found at: " + buildLog.absolutePath

def log = buildLog.text
assert log.contains("multiple parameters") :
    "Expected multi-parameter error, not found.\n--- build.log (${log.length()} bytes) ---\n${log}"
