import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

class CFG {
	static class Block {
		private String name = null;
		final List<String> lines = new ArrayList<>();
		final LinkedHashSet<Block> preds = new LinkedHashSet<>();
		final LinkedHashSet<Block> succs = new LinkedHashSet<>();
		private boolean isEnd = false;
		private Block thenBlock = null;
		private Block elseBlock = null;
		private Block loopEndBlock = null;

		Block() {

		}

		Block(int number) {
			name = Integer.toString(number);
		}

		Block(String name) {
			this.name = name;
		}

		void setName(int number) {
			this.name = Integer.toString(number);
		}

		void setName(String name) {
			this.name = name;
		}

		String getName() {
			return name;
		}

		void addLine(String line) {
			if(!isEnd)
				lines.add(line);
		}

		void setThenBlock(Block block) {
			thenBlock = block;
		}

		void setElseBlock(Block block) {
			elseBlock = block;
		}

		void setLoopEndBlock(Block block) {
			loopEndBlock = block;
		}

		Block getThenBlock() {
			return thenBlock;
		}

		Block getElseBlock() {
			return elseBlock;
		}

		Block getLoopEndBlock() {
			return loopEndBlock;
		}
	}

	private final Block entryBlock;
	private final Block exitBlock;
	private final Map<Integer, Block> blocks = new TreeMap<>();
	private final Map<Block, Integer> prunedBlockIdxes = new HashMap<>();
	Block curBlock;

	private final String funcName;
	final List<String> retType = new ArrayList<>();
	final List<String> args = new ArrayList<>();

	private Integer nextBlockId = 0;
	private final Stack<Block> pastBlocks = new Stack<>();
	private final Stack<Block> endBlocks = new Stack<>();

	CFG(String funcName) {
		this.funcName = funcName;
		entryBlock = new Block("entry");
		exitBlock = new Block("exit");
		curBlock = entryBlock;
		endBlocks.push(exitBlock);
		addBlock(false);
	}

	public void addBlock(boolean isEndBlock) {
		if(curBlock.isEnd) return;
		Block pastBlock = curBlock;
		curBlock = new Block(nextBlockId);

        pastBlock.succs.remove(endBlocks.peek());
		endBlocks.peek().preds.remove(pastBlock);

		pastBlock.succs.add(curBlock);
		curBlock.preds.add(pastBlock);

		curBlock.succs.add(endBlocks.peek());
		endBlocks.peek().preds.add(curBlock);

		blocks.put(nextBlockId, curBlock);
		nextBlockId++;

		if(isEndBlock) {
			pastBlocks.push(curBlock);
			endBlocks.push(curBlock);
		}
	}

	public boolean addEndBlock() {
		if(curBlock.isEnd) return true;
		Block pastBlock = curBlock;
		curBlock = new Block();

		pastBlock.succs.remove(endBlocks.peek());
		endBlocks.peek().preds.remove(pastBlock);

		pastBlock.succs.add(curBlock);
		curBlock.preds.add(pastBlock);

		curBlock.succs.add(endBlocks.peek());
		endBlocks.peek().preds.add(curBlock);

		pastBlocks.push(curBlock);
		pastBlocks.push(pastBlock);
		endBlocks.push(curBlock);
		return false;
	}

	public void moveTopPast() {
		curBlock = pastBlocks.peek();
	}

	public boolean moveTopPastWithDeletion() {
		if(pastBlocks.empty()) return true;
		curBlock = pastBlocks.pop();
		return false;
	}

	public boolean moveTopEndWithDeletion() {
		if(pastBlocks.empty() || endBlocks.empty() || pastBlocks.peek() != endBlocks.peek()) {
			return true;
		}

		curBlock = endBlocks.peek();
		endBlocks.pop();
		pastBlocks.pop();
		if(curBlock.getName() == null) {
			curBlock.setName(nextBlockId);
			blocks.put(nextBlockId, curBlock);
			nextBlockId++;
		}
		return false;
	}

	public void changeSuccsToExit() {
		for(Block succs : curBlock.succs) {
			succs.preds.remove(curBlock);
		}
		curBlock.succs.clear();
		curBlock.succs.add(exitBlock);
		exitBlock.preds.add(curBlock);
		curBlock.isEnd = true;
	}

	public void pruning() {
		// Delete unreachable codes
		Set<Block> reachedBlocks = new HashSet<>();
		reachedBlocks.add(blocks.get(0));
		for(Block block : blocks.values()) {
			if(!reachedBlocks.contains(block)) {
				block.setName(null);
				for(Block succs : block.succs) {
					succs.preds.remove(block);
				}
				continue;
			}
            reachedBlocks.addAll(block.succs);
		}

		// Delete Empty Line Block
		for(Block block : blocks.values()) {
			if(block.getName() == null) continue;
			if(block.lines.isEmpty()) {
				for(Block preds : block.preds) {
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
				for(Block succs : block.succs) {
					succs.preds.remove(block);
					succs.preds.addAll(block.preds);
				}
				block.setName(null);
			}
		}

		// Merging blocks
		for(Block block : blocks.values()) {
			if(block.getName() == null) continue;

			while(true) {
				if(block.succs.size() != 1) break;

				Block nxt = block.succs.iterator().next();
				if (nxt.preds.size() != 1 || nxt.getName().equals("exit")) break;

				block.lines.addAll(nxt.lines);

				block.succs.remove(nxt);
				for (Block nnxt : nxt.succs) {
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
		for(Block block : blocks.values()) {
			if(block.getName() == null) continue;
			prunedBlockIdxes.put(block, idx++);
		}
	}

	private String getBlockName(Block block) {
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

	private List<String> getSortedBlocks(LinkedHashSet<Block> blocks) {
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

	private void printPredsSuccs(Block block) {
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

	private void printBlock(Block block) {
		String blockName = getBlockName(block);
		if(blockName == null) return;

		boolean doHaveThenBlock = block.getThenBlock() != null && block.getThenBlock().getName() != null;
		boolean doHaveLoopEndBlock = block.getLoopEndBlock() != null;

		System.out.println("@" + blockName + " {");
//		System.out.println(block.getLoopEndBlock() == null);
		// Print lines that in Block
		int len = 4;
		int stmtNum = 0;
		boolean stmt = false;
		boolean doPrintCompountStmt = false;

		for(String line : block.lines) {
			if(line.equals("if") || line.equals("for") || line.equals("while")) {
				stmtNum++;
			}
		}

		for (String line : block.lines) {
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

		for(Block block : blocks.values()) {
			printBlock(block);
		}
		printBlock(exitBlock);
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
			curCFG.curBlock.addLine(str);
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

		if(curCFG.addEndBlock()) {
			return null;
		}

		curCFG.moveTopPast();

		CFG.Block ifBlock = curCFG.curBlock;
		curCFG.addBlock(false);
		ifBlock.setThenBlock(curCFG.curBlock);
		if(ctx.stmt(0).getChild(0) instanceof simpleCParser.CompoundStmtContext) {
			brackets++;
		}
		visit(ctx.stmt(0));

		curCFG.moveTopPast();
		curCFG.addBlock(false);
		ifBlock.setElseBlock(curCFG.curBlock);
		if(ctx.stmt().size() >= 2) {
			if(ctx.stmt(1).getChild(0) instanceof simpleCParser.CompoundStmtContext) {
				brackets++;
			}
			visit(ctx.stmt(1));
		}

		curCFG.moveTopPastWithDeletion();
		curCFG.moveTopEndWithDeletion();
		return null;
	}

	@Override
	public Void visitWhileStmt(simpleCParser.WhileStmtContext ctx) {
		curCFG.addBlock(true);
		addLine("    ");
		addLine(ctx.getChild(0).getText());
		visit(ctx.LPAREN());
		visit(ctx.expr());
		visit(ctx.RPAREN());

		curCFG.addBlock(false);
		if(ctx.stmt().getChild(0) instanceof simpleCParser.CompoundStmtContext) {
			brackets++;
		}
		visit(ctx.stmt());

		if(curCFG.moveTopEndWithDeletion()) {
			return null;
		}

		CFG.Block whileBlock = curCFG.curBlock;
		curCFG.addBlock(false);
		whileBlock.setLoopEndBlock(curCFG.curBlock);
		return null;
	}

	@Override
	public Void visitForStmt(simpleCParser.ForStmtContext ctx) {
		addLine("    ");
		visit(ctx.assign(0));
		visit(ctx.SEMI(0));
		addLine("\n");

		curCFG.addBlock(true);
		addLine("    ");
		addLine(ctx.getChild(0).getText());
		visit(ctx.LPAREN());
		visit(ctx.SEMI(0));
		visit(ctx.expr());
		visit(ctx.SEMI(1));
		visit(ctx.RPAREN());

		curCFG.addBlock(false);
		if(curCFG.addEndBlock()) {
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

		curCFG.moveTopPastWithDeletion();
		curCFG.moveTopEndWithDeletion();
		curCFG.moveTopEndWithDeletion();
		CFG.Block forBlock = curCFG.curBlock;
		curCFG.addBlock(false);
		forBlock.setLoopEndBlock(curCFG.curBlock);
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
		simpleCLexer lexer = new simpleCLexer(CharStreams.fromFileName(inputFile));

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
