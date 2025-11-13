import java.lang.*;
import java.util.*;
import java.io.*;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

/**
 * Basic BasicBlock: Node of Control Flow Graph
 */
class BasicBlock {
	/** Basic Components */

	// Name of BasicBlock. e.g. "entry", "exit", "0", "1", ...
	private String name;

	// codes that in BasicBlock
	private final List<String> lines = new ArrayList<>();

	// whether successor is exit block
	private boolean isEnd = false;


	/** Preds/Succs of current BasicBlock */

	// predecessors of BasicBlocks
	final LinkedHashSet<BasicBlock> preds = new LinkedHashSet<>();

	// successors of BasicBlocks
	final LinkedHashSet<BasicBlock> succs = new LinkedHashSet<>();


	/** Next Block of If/For/While statement */

	// then block of if statement (only this block is if statement)
	private BasicBlock thenBlock = null;

	// else block of if statement (only this block is if statement)
	private BasicBlock elseBlock = null;

	// loop end block of for/while statement (only this block is for/while statement)
	private BasicBlock loopEndBlock = null;


	/**
	 * Add a line to BasicBlock
	 *
	 * @param line		line that in code of BasicBlock to be added
	 */
	void addLine(String line) {
		if(!isEnd)
			lines.add(line);
	}

	/**
	 * Add lines to BasicBlock
	 *
	 * @param lines		lines that in code of BasicBlock to be added
	 */
	void addAllLines(List<String> lines) {
		if(!isEnd)
			this.lines.addAll(lines);
	}


	/** Constructors */

	BasicBlock() {

	}

	BasicBlock(int number) {
		name = Integer.toString(number);
	}

	BasicBlock(String name) {
		this.name = name;
	}


	/** Getters and Setters */

	void setName(Integer number) {
		if(number == null)
			this.name = null;
		else
			this.name = Integer.toString(number);
	}

	String getName() {
		return name;
	}


	List<String> getLines() {
		return lines;
	}


	void setAsEndBlock() {
		isEnd = true;
	}

	boolean isEnd() {
		return isEnd;
	}


	void setThenBlock(BasicBlock block) {
		thenBlock = block;
	}

	BasicBlock getThenBlock() {
		return thenBlock;
	}


	void setElseBlock(BasicBlock block) {
		elseBlock = block;
	}

	BasicBlock getElseBlock() {
		return elseBlock;
	}


	void setLoopEndBlock(BasicBlock block) {
		loopEndBlock = block;
	}

	BasicBlock getLoopEndBlock() {
		return loopEndBlock;
	}
}

/**
 * Construct Control Flow Graph
 */
class CFG {
	/** Function Components */

	// function name
	private final String funcName;

	// return type
	private final List<String> retType = new ArrayList<>();

	// arguments
	private final List<String> args = new ArrayList<>();


	/** BasicBlocks in CFG */

	// entry block of CFG
	private final BasicBlock entryBlock = new BasicBlock("entry");

	// exit block of CFG
	private final BasicBlock exitBlock = new BasicBlock("exit");

	// all basic blocks in CFG except entry and exit blocks
	private final Map<Integer, BasicBlock> blocks = new TreeMap<>();

	// next block id to be assigned
	private int nextBlockId = 0;

	/** Current BasicBlock in CFG */

	// current basic block of CFG
	private BasicBlock curBlock = entryBlock;

	// stacks for segmented entry blocks
	private final Stack<BasicBlock> segmentStartBlocks = new Stack<>();

	// stacks for segmented exit blocks
	private final Stack<BasicBlock> segmentEndBlocks = new Stack<>();


	/** Pruned BasicBlock Idxes */

	// mapping from BasicBlock to pruned block idx
	private final Map<BasicBlock, Integer> prunedBlockIdxes = new HashMap<>();


	/**
	 * Constructor of CFG
	 * Mark as Exit Block to End Block; add Block 0
	 * @param funcName	function name
	 */
	CFG(String funcName) {
		this.funcName = funcName;
		segmentEndBlocks.push(exitBlock);
		addBasicBlock();
	}

	/* Methods to manipulate BasicBlocks in CFG */

	/**
	 * Create the next BasicBlock and link it with the current BasicBlock
	 * @param putInBlocks	whether to put the created BasicBlock into blocks map
	 * @return				the past BasicBlock before creating the next BasicBlock
	 */
	private BasicBlock createNextBlock(boolean putInBlocks) {
		if(curBlock.isEnd()) return null;

		BasicBlock pastBlock = curBlock;

		if(putInBlocks) {
			curBlock = new BasicBlock(nextBlockId);
		}
		else {
			curBlock = new BasicBlock();
		}

		pastBlock.succs.remove(segmentEndBlocks.peek());
		segmentEndBlocks.peek().preds.remove(pastBlock);

		pastBlock.succs.add(curBlock);
		curBlock.preds.add(pastBlock);

		curBlock.succs.add(segmentEndBlocks.peek());
		segmentEndBlocks.peek().preds.add(curBlock);

		if(putInBlocks) {
			blocks.put(nextBlockId, curBlock);
			nextBlockId++;
		}

		return pastBlock;
	}

	/**
	 * Add a basic block after the current basic block
	 */
	public void addBasicBlock() {
		createNextBlock(true);
	}

	/**
	 * Add a loop block after the current basic block
	 * @return	true if failed to add loop block
	 */
	public boolean addLoopBlock() {
		BasicBlock pastBlock = createNextBlock(true);
		if(pastBlock == null) return true;

		segmentStartBlocks.push(curBlock);
		segmentEndBlocks.push(curBlock);
		return false;
	}

	/**
	 * Add a segment block after the current basic block
	 * @return	true if failed to add segment block
	 */
	public boolean addSegmentBlock() {
		BasicBlock pastBlock = createNextBlock(false);
		if(pastBlock == null) return true;

		segmentStartBlocks.push(pastBlock);
		segmentEndBlocks.push(curBlock);
		return false;
	}


	/**
	 * Move current block to the top segment start block
	 */
	public void moveTopPast() {
		curBlock = segmentStartBlocks.peek();
	}

	/**
	 * Move out of the current segment scope
	 * @return	true if no segment to move out
	 */
	public boolean moveOutOfScope() {
		if(segmentStartBlocks.empty() || segmentEndBlocks.empty()) {
			return true;
		}

		curBlock = segmentEndBlocks.peek();
		segmentEndBlocks.pop();
		segmentStartBlocks.pop();

		if(curBlock.getName() == null) {
			curBlock.setName(nextBlockId);
			blocks.put(nextBlockId, curBlock);
			nextBlockId++;
		}
		return false;
	}

	/**
	 * Change all successors of current block to exit block
	 */
	public void changeSuccsToExit() {
		for(BasicBlock succs : curBlock.succs) {
			succs.preds.remove(curBlock);
		}
		curBlock.succs.clear();
		curBlock.succs.add(exitBlock);
		exitBlock.preds.add(curBlock);
		curBlock.setAsEndBlock();
	}


	/* Pruning and Printing Methods */

	/**
	 * Pruning the CFG:
	 * 1. Delete unreachable codes
	 * 2. Delete Empty Line BasicBlock
	 * 3. Merging blocks
	 * 4. Renaming block Ids
	 */
	public void pruning() {
		// 1. Delete unreachable codes
		Set<BasicBlock> reachedBlocks = new HashSet<>();
		reachedBlocks.add(blocks.get(0));
		for(BasicBlock block : blocks.values()) {
			if(!reachedBlocks.contains(block)) {
				block.setName(null);
				for(BasicBlock succs : block.succs) {
					succs.preds.remove(block);
				}
				continue;
			}
            reachedBlocks.addAll(block.succs);
		}

		// 2. Delete Empty Line BasicBlock
		for(BasicBlock block : blocks.values()) {
			if(block.getName() == null) continue;
			if(block.getLines().isEmpty()) {
				for(BasicBlock preds : block.preds) {
					if(preds.getThenBlock() == block) {
						preds.setThenBlock(block.succs.iterator().next());
					}
					if(preds.getElseBlock() == block) {
						preds.setElseBlock(block.succs.iterator().next());
					}
					if(preds.getLoopEndBlock() == block) {
						preds.setLoopEndBlock(block.succs.iterator().next());
					}
					preds.succs.remove(block);
                    preds.succs.addAll(block.succs);
				}
				for(BasicBlock succs : block.succs) {
					succs.preds.remove(block);
					succs.preds.addAll(block.preds);
				}
				block.setName(null);
			}
		}

		// 3. Merging blocks
		for(BasicBlock block : blocks.values()) {
			if(block.getName() == null) continue;

			while(true) {
				if(block.succs.size() != 1) break;

				BasicBlock nxt = block.succs.iterator().next();
				if (nxt.preds.size() != 1 || nxt.getName().equals("exit")) break;

				block.addAllLines(nxt.getLines());

				block.succs.remove(nxt);
				for (BasicBlock nnxt : nxt.succs) {
					nnxt.preds.remove(nxt);
					nnxt.preds.add(block);
					block.succs.add(nnxt);
				}

				block.setThenBlock(nxt.getThenBlock());
				block.setElseBlock(nxt.getElseBlock());
				block.setLoopEndBlock(nxt.getLoopEndBlock());

				nxt.setName(null);
			}
		}

		// 4. Renaming block Ids
		int idx = 0;
		for(BasicBlock block : blocks.values()) {
			if(block.getName() == null) continue;
			prunedBlockIdxes.put(block, idx++);
		}
	}

	/**
	 * Get the name of a BasicBlock after pruning
	 * @param block		BasicBlock to get name
	 * @return			name of BasicBlock after pruning
	 */
	private String getBlockName(BasicBlock block) {
		String name = block.getName();
		if(name == null) {
			return null;
		}
		else if(name.equals("entry") || name.equals("exit")) {
			return funcName + "_" + name;
		}
		else {
			return funcName + "_B" + prunedBlockIdxes.get(block);
		}
	}

	/**
	 * Print a list of lines
	 * @param lines		list of lines to be printed
	 */
	private void printLine(List<String> lines) {
		for (String line : lines) {
			System.out.print(line);
		}
	}

	/**
	 * Get sorted block names from a set of BasicBlocks
	 * @param blocks		LinkedHashSet of BasicBlocks
	 * @return				sorted list of block names
	 */
	private List<String> getSortedBlocks(LinkedHashSet<BasicBlock> blocks) {
		return blocks.stream()
				.sorted((a, b) -> {
					String nameA = a.getName();
					String nameB = b.getName();

					boolean numA = nameA.matches("\\d+");
					boolean numB = nameB.matches("\\d+");

					if(numA && numB) {
						return Integer.compare(Integer.parseInt(nameA), Integer.parseInt(nameB));
					} else if(numA) {
						return -1;
					} else if(numB) {
						return 1;
					} else {
						return nameA.compareTo(nameB);
					}
				})
				.map(this::getBlockName)
				.collect(Collectors.toList());
	}

	/**
	 * Print predecessors and successors of a BasicBlock as sorted list
	 * @param block		BasicBlock to be printed
	 */
	private void printPredsSuccs(BasicBlock block) {
		String preds = String.join(", ", getSortedBlocks(block.preds));

		System.out.print("Predecessors: ");
		if(preds.isEmpty()) {
			System.out.println("-");
		} else {
			System.out.println(preds);
		}

		String succs = String.join(", ", getSortedBlocks(block.succs));

		System.out.print("Successors: ");
		if(succs.isEmpty()) {
			System.out.println("-");
		} else {
			System.out.println(succs);
		}
		System.out.println();
	}

	/**
	 * Print a BasicBlock
	 * @param block		BasicBlock to be printed
	 */
	private void printBlock(BasicBlock block) {
		String blockName = getBlockName(block);
		if(blockName == null) return;

		// check if block and for/while block
		boolean doHaveThenBlock = block.getThenBlock() != null && block.getThenBlock().getName() != null;
		boolean doHaveLoopEndBlock = block.getLoopEndBlock() != null;

		// Print block header
		System.out.println("@" + blockName + " {");

		// Print lines that in BasicBlock
		int len = 4;
		int stmtNum = 0;
		boolean stmt = false;
		boolean doPrintCompountStmt = false;

		// count total if/for/while statements
		for(String line : block.getLines()) {
			if(line.equals("if") || line.equals("for") || line.equals("while")) {
				stmtNum++;
			}
		}

		// print lines
		for (String line : block.getLines()) {
			// check if last if/for/while statement to print then/else/loop_end block num
			if(line.equals("if") || line.equals("for") || line.equals("while")) {
				stmtNum--;
				if(stmtNum == 0) {
					if(line.equals("if")) {
						stmt = doHaveThenBlock;
					}
					else {
						stmt = true;
					}
				}
			}

			// print empty compound statement if no then/else block
			if(line.equals("if") && !stmt) {
				doPrintCompountStmt = true;
			}

			if(doPrintCompountStmt && line.equals("    ")) {
				System.out.println("{ }");
				doPrintCompountStmt = false;
			}
			System.out.print(line);

			if(stmt) len += line.length();
		}

		// print empty compound statement if needed
		if(doPrintCompountStmt) {
			System.out.println("{ }");
		}

		// print then/else/loop_end block num
		if(doHaveThenBlock) {
			len += 4;
			System.out.print("    ");
			System.out.print("# then: " + getBlockName(block.getThenBlock()));
			System.out.println();

			System.out.print(" ".repeat(len));
			System.out.print("# else: " + getBlockName(block.getElseBlock()));
			System.out.println();
		}
		else if(doHaveLoopEndBlock) {
			System.out.print("    ");
			System.out.print("# loop_end: " + getBlockName(block.getLoopEndBlock()));
			System.out.println();
		}

		System.out.println("}");

		// Print preds/succs
		printPredsSuccs(block);
	}

	/**
	 * Print the result of CFG
	 */
	public void print() {
		// Print function header
		System.out.println("@" + getBlockName(entryBlock) + " {");

		// Print function name
		System.out.println("   name: " + funcName);

		// Print return type
		System.out.print("   ret_type: ");
		printLine(retType);
		System.out.println();

		// Print arguments
		System.out.print("   args: ");
		if(args.isEmpty()) {
			System.out.print("-");
		}
		else {
			printLine(args);
		}
		System.out.println();

		System.out.println("}");

		// Print entry block preds/succs
		printPredsSuccs(entryBlock);

		// Print all basic blocks
		for(BasicBlock block : blocks.values()) {
			printBlock(block);
		}

		// Print exit block
		printBlock(exitBlock);

	}

	/** Getters */

	BasicBlock getCurrentBlock() {
		return curBlock;
	}

	List<String> getRetType() {
		return retType;
	}

	List<String> getArgs() {
		return args;
	}
}

class Global {
	final List<String> lines = new ArrayList<>();

	void print() {
		if(lines.isEmpty()) return;

		System.out.println("@Globals {");
		for (String line : lines) {
			System.out.print(line);
		}
		System.out.println("}");
		System.out.println("Predecessors: -");
		System.out.println("Successors: -");
		System.out.println();
	}
}

class CFAVisitor extends simpleCBaseVisitor<Void> {
	private final Global global = new Global();
	private final List<CFG> cfgs = new ArrayList<>();
	private CFG curCFG = null;
	private List<String> curLine = null;

	// hash-map:
	Map<String, Integer> vars = new HashMap<String, Integer>();
	// stack:
	Stack<Integer> evalStack = new Stack<Integer>();

	// add more fields you need …

	/**
	 * Add a line to current line (global or current block)
	 * @param str	line to be added
	 */
	private void addLine(String str) {
		if(curLine != null) {
			curLine.add(str);
		}
		else if(curCFG == null) {
			global.lines.add(str);
		}
		else {
			curCFG.getCurrentBlock().addLine(str);
		}
	}

	/**
	 * Visit program, visit declList and funcList, print global and each CFG
	 * @param ctx the parse tree
	 * @return    null
	 */
	@Override
	public Void visitProgram(simpleCParser.ProgramContext ctx) {
		System.out.println("/*--- program: " + CFGBuilder.inputFile + " ---*/");

		if(ctx.declList() != null) {
			visit(ctx.declList());
		}

		visit(ctx.funcList());

		global.print();
		for(CFG cfg : cfgs) {
			cfg.pruning();
			cfg.print();
		}
		return null;
	}

	/**
	 * Visit function, create new CFG for each function
	 * @param ctx the parse tree
	 * @return    null
	 */
	@Override
	public Void visitFunction(simpleCParser.FunctionContext ctx) {
		String funcName = ctx.ID().getText();
		curCFG = new CFG(funcName);
		cfgs.add(curCFG);

		curLine = curCFG.getRetType();
		visit(ctx.type());

		curLine = curCFG.getArgs();
		if(ctx.paramList() != null) {
			visit(ctx.paramList());
		}

		curLine = null;
		visit(ctx.compoundStmt());
		return null;
	}

	/**
	 * Visit declaration list, visit each declaration with indentation and newline
	 * @param ctx the parse tree
	 * @return    null
	 */
	@Override
	public Void visitDeclList(simpleCParser.DeclListContext ctx) {
		for(int i = 0; i < ctx.getChildCount(); i++) {
			addLine("    ");
			visit(ctx.getChild(i));
			addLine("\n");
		}
		return null;
	}

	/**
	 * Visit assignment statement, add indentation and newline
	 * @param ctx the parse tree
	 * @return    null
	 */
	@Override
	public Void visitAssignStmt(simpleCParser.AssignStmtContext ctx) {
		addLine("    ");
		visitChildren(ctx);
		addLine("\n");
		return null;
	}

	/**
	 * Visit call statement, add indentation and newline
	 * @param ctx the parse tree
	 * @return    null
	 */
	@Override
	public Void visitCallStmt(simpleCParser.CallStmtContext ctx) {
		addLine("    ");
		visitChildren(ctx);
		addLine("\n");
		return null;
	}

	/**
	 * Visit statement, add indentation and newline for empty statement
	 * @param ctx the parse tree
	 * @return    null
	 */
	@Override
	public Void visitStmt(simpleCParser.StmtContext ctx) {
		if(ctx.SEMI() != null) {
			addLine("    ");
			visitChildren(ctx);
			addLine("\n");
			return null;
		}
		visitChildren(ctx);
		return null;
	}

	/**
	 * Visit if statement, add condition statement and then/else statements
	 * @param ctx the parse tree
	 * @return    null
	 */
	@Override
	public Void visitIfStmt(simpleCParser.IfStmtContext ctx) {

		// 1. Add if statement in current block
		addLine("    ");
		addLine(ctx.getChild(0).getText());
		visit(ctx.LPAREN());
		visit(ctx.expr());
		visit(ctx.RPAREN());

		// 2. Add Segment Block for then/else statements
		if(curCFG.addSegmentBlock()) {
			return null;
		}

		// 3. Move to if statement
		curCFG.moveTopPast();
		BasicBlock ifBlock = curCFG.getCurrentBlock();

		// 4. Add Basic Block for then statement and visit
		curCFG.addBasicBlock();
		ifBlock.setThenBlock(curCFG.getCurrentBlock());
		visit(ctx.stmt(0));

		// 5. Move back to if statement
		curCFG.moveTopPast();

		// 6. Add Basic Block for else statement and visit
		curCFG.addBasicBlock();
		ifBlock.setElseBlock(curCFG.getCurrentBlock());
		if(ctx.stmt().size() >= 2) {
			visit(ctx.stmt(1));
		}

		// 7. Move out of scope
		curCFG.moveOutOfScope();

		return null;
	}

	/**
	 * Visit while statement, add condition statement and loop body
	 * @param ctx the parse tree
	 * @return	  null
	 */
	@Override
	public Void visitWhileStmt(simpleCParser.WhileStmtContext ctx) {
		// 1. Add loop block for condition statement
		if(curCFG.addLoopBlock()) {
			return null;
		}
		addLine("    ");
		addLine(ctx.getChild(0).getText());
		visit(ctx.LPAREN());
		visit(ctx.expr());
		visit(ctx.RPAREN());

		// 2. Add Basic Block for loop body and visit
		curCFG.addBasicBlock();
		visit(ctx.stmt());

		// 3. Move out of scope
		if(curCFG.moveOutOfScope()) {
			return null;
		}

		BasicBlock whileBlock = curCFG.getCurrentBlock();

		// 4. Add Basic Block for loop end
		curCFG.addBasicBlock();
		whileBlock.setLoopEndBlock(curCFG.getCurrentBlock());

		return null;
	}

	/**
	 * Visit for statement, add initialization, condition, update statements and loop body
	 * @param ctx the parse tree
	 * @return	  null
	 */
	@Override
	public Void visitForStmt(simpleCParser.ForStmtContext ctx) {

		// 1. initialization statement
		addLine("    ");
		visit(ctx.assign(0));
		visit(ctx.SEMI(0));
		addLine("\n");

		// 2. Add loop block for condition statement
		if(curCFG.addLoopBlock()) {
			return null;
		}
		addLine("    ");
		addLine(ctx.getChild(0).getText());
		visit(ctx.LPAREN());
		visit(ctx.SEMI(0));
		visit(ctx.expr());
		visit(ctx.SEMI(1));
		visit(ctx.RPAREN());

		// 3. Add Basic Block for loop body
		curCFG.addBasicBlock();

		// 4. Add Segment Block for update statement
		if(curCFG.addSegmentBlock()) {
			return null;
		}
		addLine("    ");
		visit(ctx.assign(1));
		visit(ctx.SEMI(0));
		addLine("\n");

		// 5. Move to loop body and visit
		curCFG.moveTopPast();
		visit(ctx.stmt());

		// 6. Move out of scope twice
		curCFG.moveOutOfScope();
		curCFG.moveOutOfScope();

		BasicBlock forBlock = curCFG.getCurrentBlock();

		// 7. Add Basic Block for loop end
		curCFG.addBasicBlock();
		forBlock.setLoopEndBlock(curCFG.getCurrentBlock());

		return null;
	}

	/**
	 * Visit compound statement, visit declList and stmtList. Not adding brackets
	 * @param ctx the parse tree
	 * @return	  null
	 */
	@Override
	public Void visitCompoundStmt(simpleCParser.CompoundStmtContext ctx) {
		if(ctx.declList() != null) {
			visit(ctx.declList());
		}
		visit(ctx.stmtList());
		return null;
	}

	/**
	 * Visit return statement, add line and change successors to exit block
	 * @param ctx	the parse tree
	 * @return 		null
	 */
	@Override
	public Void visitRetStmt(simpleCParser.RetStmtContext ctx) {
		addLine("    ");
		visitChildren(ctx);
		addLine("\n");
		curCFG.changeSuccsToExit();
		return null;
	}

	/**
	 * Visit a terminal node, and add its text to the current line
	 * @param node 	the terminal node to visit
	 * @return 		null
	 */
	@Override
	public Void visitTerminal(TerminalNode node) {
		String t = node.getText();
		addLine(t + " ");
		return null;
	}
}

public class CFGBuilder {
	public static String inputFile;

	public static void main(String[] args) throws IOException{

		if (args.length < 1) {
			System.out.println("    [Usage]	java CFGBuilder <input.c>");
			return;
		}
		inputFile = args[0];

		// Get lexer
		simpleCLexer lexer= new simpleCLexer(CharStreams.fromFileName(inputFile));

		// Get a list of matched tokens
		CommonTokenStream tokens = new CommonTokenStream(lexer);

		// Pass tokens to parser
		simpleCParser parser = new simpleCParser(tokens);

		// Get the root of parse tree
		ParseTree tree = parser.program();

		// Visit parse-tree
		CFAVisitor visitor = new CFAVisitor();
		visitor.visit(tree);
	}
}
