PROG=CFGBuilder

GRAMMAR=simpleC.g4
BUILDDIR=build

ANTLR_JAR=/usr/local/lib/antlr-complete.jar
ANTLR4=java -jar $(ANTLR_JAR) 
CLASSPATH=$(BUILDDIR):$(ANTLR_JAR):.

INPUT=example.c
OUTPUT=cfg.out

.PHONY: all antlr $(PROG) run clean

all: antlr $(PROG) 

# Using ANTLR, create Parser for the grammar -- simpleC.g4
antlr: $(GRAMMAR) 
	@mkdir -p $(BUILDDIR)
	$(ANTLR4) $(GRAMMAR) -o $(BUILDDIR) -no-listener -visitor 
	javac -classpath $(CLASSPATH) -d $(BUILDDIR) $(BUILDDIR)/*.java

# Create $(PROG) -- CFG builder
$(PROG): antlr $(PROG).java
	javac -classpath $(CLASSPATH) -d $(BUILDDIR) $(PROG).java

# Execute $(PROG) -- CFGBuilder
run_cfa: all
	java -classpath $(CLASSPATH) $(PROG) $(INPUT) > $(OUTPUT)
	@echo ""
	@echo "--- CFG analyzed in $(OUTPUT) ---"
	@cat $(OUTPUT)

clean:
	rm -rf $(BUILDDIR)
	@if [ -f $(OUTPUT) ]; then rm $(OUTPUT); fi

