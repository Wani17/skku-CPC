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
	final List<String> retType = new ArrayList<>();

	// arguments
	final List<String> args = new ArrayList<>();


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

	/** Methods to manipulate BasicBlocks in CFG */

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


	/** Pruning and Printing Methods */

	public void pruning() {
		// Delete unreachable codes
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

		// Delete Empty Line BasicBlock
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

		// Merging blocks
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

		// Renaming block Ids
		int idx = 0;
		for(BasicBlock block : blocks.values()) {
			if(block.getName() == null) continue;
			prunedBlockIdxes.put(block, idx++);
		}
	}

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

	private int printLine(List<String> lines) {
		int lenStmt = 4;
		boolean stmt = false;
		for (String line : lines) {
			if(line.equals("if") || line.equals("for") || line.equals("while")) {
				stmt = true;
			}
			System.out.print(line);

			if(stmt) lenStmt += line.length();
		}

		return lenStmt;
	}

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

	private void printBlock(BasicBlock block) {
		String blockName = getBlockName(block);
		if(blockName == null) return;

		boolean doHaveThenBlock = block.getThenBlock() != null && block.getThenBlock().getName() != null;
		boolean doHaveLoopEndBlock = block.getLoopEndBlock() != null;

		System.out.println("@" + blockName + " {");

		// Print lines that in BasicBlock
		int len = 4;
		int stmtNum = 0;
		boolean stmt = false;
		boolean doPrintCompountStmt = false;

		for(String line : block.getLines()) {
			if(line.equals("if") || line.equals("for") || line.equals("while")) {
				stmtNum++;
			}
		}

		for (String line : block.getLines()) {
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

		printPredsSuccs(block);
	}

	public void print() {
		System.out.println("@" + getBlockName(entryBlock) + " {");

		System.out.println("   name: " + funcName);

		System.out.print("   ret_type: ");
		printLine(retType);
		System.out.println();

		System.out.print("   args: ");
		if(args.isEmpty()) {
			System.out.print("-");
		}
		else {
			printLine(args);
		}
		System.out.println();


		System.out.println("}");
		printPredsSuccs(entryBlock);

		for(BasicBlock block : blocks.values()) {
			printBlock(block);
		}
		printBlock(exitBlock);
	}

	BasicBlock getCurrentBlock() {
		return curBlock;
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
	private int brackets = 0;

	// hash-map:
	Map<String, Integer> vars = new HashMap<String, Integer>();
	// stack:
	Stack<Integer> evalStack = new Stack<Integer>();

	// add more fields you need …

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

	@Override
	public Void visitFunction(simpleCParser.FunctionContext ctx) {
		String funcName = ctx.ID().getText();
		curCFG = new CFG(funcName);
		cfgs.add(curCFG);

		curLine = curCFG.retType;
		visit(ctx.type());

		curLine = curCFG.args;
		if(ctx.paramList() != null) {
			visit(ctx.paramList());
		}

		curLine = null;
		brackets++;
		visit(ctx.compoundStmt());
		return null;
	}

	@Override
	public Void visitDeclList(simpleCParser.DeclListContext ctx) {
		for(int i = 0; i < ctx.getChildCount(); i++) {
			addLine("    ");
			visit(ctx.getChild(i));
			addLine("\n");
		}
		return null;
	}

	@Override
	public Void visitAssignStmt(simpleCParser.AssignStmtContext ctx) {
		addLine("    ");
		visitChildren(ctx);
		addLine("\n");
		return null;
	}

	@Override
	public Void visitCallStmt(simpleCParser.CallStmtContext ctx) {
		addLine("    ");
		visitChildren(ctx);
		addLine("\n");
		return null;
	}

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

	@Override
	public Void visitIfStmt(simpleCParser.IfStmtContext ctx) {
		addLine("    ");
		addLine(ctx.getChild(0).getText());
		visit(ctx.LPAREN());
		visit(ctx.expr());
		visit(ctx.RPAREN());

		if(curCFG.addSegmentBlock()) {
			return null;
		}

		curCFG.moveTopPast();

		BasicBlock ifBlock = curCFG.getCurrentBlock();
		curCFG.addBasicBlock();

		ifBlock.setThenBlock(curCFG.getCurrentBlock());
		if(ctx.stmt(0).getChild(0) instanceof simpleCParser.CompoundStmtContext) {
			brackets++;
		}
		visit(ctx.stmt(0));

		curCFG.moveTopPast();
		curCFG.addBasicBlock();
		ifBlock.setElseBlock(curCFG.getCurrentBlock());
		if(ctx.stmt().size() >= 2) {
			if(ctx.stmt(1).getChild(0) instanceof simpleCParser.CompoundStmtContext) {
				brackets++;
			}
			visit(ctx.stmt(1));
		}

		curCFG.moveOutOfScope();
		return null;
	}

	@Override
	public Void visitWhileStmt(simpleCParser.WhileStmtContext ctx) {
		if(curCFG.addLoopBlock()) {
			return null;
		}
		addLine("    ");
		addLine(ctx.getChild(0).getText());
		visit(ctx.LPAREN());
		visit(ctx.expr());
		visit(ctx.RPAREN());

		curCFG.addBasicBlock();
		if(ctx.stmt().getChild(0) instanceof simpleCParser.CompoundStmtContext) {
			brackets++;
		}
		visit(ctx.stmt());

		if(curCFG.moveOutOfScope()) {
			return null;
		}

		BasicBlock whileBlock = curCFG.getCurrentBlock();
		curCFG.addBasicBlock();
		whileBlock.setLoopEndBlock(curCFG.getCurrentBlock());
		return null;
	}

	@Override
	public Void visitForStmt(simpleCParser.ForStmtContext ctx) {
		addLine("    ");
		visit(ctx.assign(0));
		visit(ctx.SEMI(0));
		addLine("\n");

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

		curCFG.addBasicBlock();
		if(curCFG.addSegmentBlock()) {
			return null;
		}
		addLine("    ");
		visit(ctx.assign(1));
		visit(ctx.SEMI(0));
		addLine("\n");

		curCFG.moveTopPast();
		if(ctx.stmt().getChild(0) instanceof simpleCParser.CompoundStmtContext) {
			brackets++;
		}
		visit(ctx.stmt());

		curCFG.moveOutOfScope();
		curCFG.moveOutOfScope();
		BasicBlock forBlock = curCFG.getCurrentBlock();
		curCFG.addBasicBlock();
		forBlock.setLoopEndBlock(curCFG.getCurrentBlock());
		return null;
	}

	@Override
	public Void visitCompoundStmt(simpleCParser.CompoundStmtContext ctx) {
		if(ctx.declList() != null) {
			visit(ctx.declList());
		}
		visit(ctx.stmtList());
		return null;
	}

	@Override
	public Void visitRetStmt(simpleCParser.RetStmtContext ctx) {
		addLine("    ");
		visitChildren(ctx);
		addLine("\n");
		curCFG.changeSuccsToExit();
		return null;
	}

	@Override
	public Void visitTerminal(TerminalNode node) {
		String t = node.getText();
		addLine(t + " ");
		return null;
	}

	// add more methods you need …
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
