def generatedDir = new File(basedir, "target/generated-sources/annotations/dev/negotex/test")
def buildLog = new File(basedir, "build.log").text
def ctx = "\ngenerated dir exists: ${generatedDir.exists()}" +
          "\ngenerated dir contents: ${generatedDir.list()?.join(', ') ?: '(empty or missing)'}" +
          "\n--- build.log (${buildLog.length()} bytes) ---\n${buildLog}"

def extractor = new File(generatedDir, "CreditCheckInputExtractor.java")
assert extractor.exists() : "CreditCheckInputExtractor.java was not generated." + ctx
assert extractor.text.contains("implements PayloadExtractor<CreditCheckInput>")
assert extractor.text.contains('"application"')
assert extractor.text.contains('"customerProfile"')
assert extractor.text.contains("new CreditCheckInput(")
assert extractor.text.contains("do not edit")

def inserter = new File(generatedDir, "CreditCheckResultInserter.java")
assert inserter.exists() : "CreditCheckResultInserter.java was not generated." + ctx
assert inserter.text.contains("implements PayloadInserter<CreditCheckResult>")
assert inserter.text.contains('"creditScore"')
assert inserter.text.contains('"riskBand"')
assert inserter.text.contains('"approved"')
assert inserter.text.contains("output.score()")
assert inserter.text.contains("output.riskBand()")
assert inserter.text.contains("output.approved()")
