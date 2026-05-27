def generatedDir = new File(basedir, "target/generated-sources/annotations/dev/negotex/test")
def buildLog = new File(basedir, "build.log").text
def ctx = "\ngenerated dir exists: ${generatedDir.exists()}" +
          "\ngenerated dir contents: ${generatedDir.list()?.join(', ') ?: '(empty or missing)'}" +
          "\n--- build.log (${buildLog.length()} bytes) ---\n${buildLog}"

def extractor = new File(generatedDir, "StringExtractor.java")
assert extractor.exists() : "StringExtractor.java was not generated." + ctx
assert extractor.text.contains("implements PayloadExtractor<String>")
assert extractor.text.contains('"application"')
assert extractor.text.contains("do not edit")

def inserter = new File(generatedDir, "IntegerInserter.java")
assert inserter.exists() : "IntegerInserter.java was not generated." + ctx
assert inserter.text.contains("implements PayloadInserter<Integer>")
assert inserter.text.contains('"creditScore"')
