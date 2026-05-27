def generatedDir = new File(basedir, "target/generated-sources/annotations/dev/negotex/test")
def buildLog = new File(basedir, "build.log").text
def ctx = "\ngenerated dir exists: ${generatedDir.exists()}" +
          "\ngenerated dir contents: ${generatedDir.list()?.join(', ') ?: '(empty or missing)'}" +
          "\n--- build.log (${buildLog.length()} bytes) ---\n${buildLog}"

// Form B — extractor and inserter
assert new File(generatedDir, "RiskInputExtractor.java").exists() :
    "RiskInputExtractor.java was not generated." + ctx
assert new File(generatedDir, "RiskResultInserter.java").exists() :
    "RiskResultInserter.java was not generated." + ctx

// Form A — extractor and inserter
assert new File(generatedDir, "StringExtractor.java").exists() :
    "StringExtractor.java was not generated." + ctx
assert new File(generatedDir, "BooleanInserter.java").exists() :
    "BooleanInserter.java was not generated." + ctx
