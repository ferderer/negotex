def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log not found at: " + buildLog.absolutePath

def log = buildLog.text

// Both errors must appear — proves the processor accumulates all errors rather than
// stopping at the first failure.
assert log.contains("must be annotated with @PayloadKey") :
    "Expected missing-key error, not found.\n--- build.log (${log.length()} bytes) ---\n${log}"
assert log.contains("Duplicate @PayloadKey value 'amount'") :
    "Expected duplicate-key error, not found.\n--- build.log (${log.length()} bytes) ---\n${log}"
