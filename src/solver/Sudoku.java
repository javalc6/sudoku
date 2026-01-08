package solver;
/**
The class solver.Sudoku contains utilities to generate and solve Sudoku game

Note: ordinary values are in 1..9 range, value 0 is used for a free place

24-10-2023: initial release
02-11-2023: added method solveBM() to solve sudoku using bitmaps for speed improvement
03-11-2023: added parameter -benchmark to perform benchmarks
04-11-2023: added method generate() to generate random sudoku board using solver based algorithm
05-11-2023: added method fastsolveBM() to solve sudoku using bitmaps and forbidden tables for speed improvement
06-11-2023: added method solveDLX() to solve the sudoku board using DLX Sudoku solver developed by Shivan Kaul Sahib
06-01-2026: refactored code, can be used as standalone for test or as solver by another app
07-01-2026: implemented minimum remaining values (MRV) heuristic in solve(), improved benchmarking

Usage: java solver.Sudoku [-benchmark | <board>]

without parameters: it runs random tests forever
with parameters:
-benchmark: performs benchmark using a specific set of boards
<board>: 81 characters string defining a Sudoku board, e.g. .......12........3..23..4....18....5.6..7.8.......9.....85.....9...4.5..47...6...

*/
import java.util.function.Function;
import java.util.HashSet;
import java.util.Random;

import dlx.SudokuDLX;

public class Sudoku {
	private static final int SIZE = 9;
	private static final int BLOCK_SIZE = 3;
	private static final int TOTAL_CELLS = 81;

    private final int[][] board = new int[SIZE][SIZE];
    private final int[][] template = {{1,2,3,4,5,6,7,8,9},{4,9,7,8,1,2,5,6,3},{5,8,6,3,9,7,1,4,2},{3,5,4,7,6,9,8,2,1},
		{8,6,9,2,4,1,3,5,7},{7,1,2,5,3,8,4,9,6},{9,7,1,6,8,5,2,3,4},{2,3,5,9,7,4,6,1,8},{6,4,8,1,2,3,9,7,5}};

	final static int[] value_mask = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};//mask used by method solveBM(): value_mask[k] = 2 ^ k

//generate: random sudoku board generator, it builds a board with unique solution and at least <n_values>
	public void generate(int n_values) {
		for (int i = 0; i < SIZE; i++)
			for (int j = 0; j < SIZE; j++)
				board[i][j] = 0;
		Random random = new Random();
		if (!generate(random))
			throw new RuntimeException("invalid board generated");//never occurs

//here we have full board, now we clear cells in random order
		int[] shuffle = new int[TOTAL_CELLS];
		for (int i = 0; i < TOTAL_CELLS; i++)
			shuffle[i] = i;
		for (int i = 0; i < TOTAL_CELLS; i++) {
			int k = random.nextInt(TOTAL_CELLS);//k in 0..80 range
			int temp = shuffle[k];
			shuffle[k] = shuffle[i];
			shuffle[i] = temp;
		}
		int values = TOTAL_CELLS;//full board
		for (int k = 0; k < TOTAL_CELLS && values > n_values; k++) {
			int i = shuffle[k] / SIZE;
			int j = shuffle[k] % SIZE;
			
			int rollback = board[i][j];
			board[i][j] = 0;
			if (uniqueSolutionExist())
				values--;
			else board[i][j] = rollback;
		}
	}

//internal function, generate full board
	private boolean generate(Random random) {
//find plain solution
		boolean found;
		do {
			found = false;
			for (int i = 0; i < SIZE; i++)	{
				for (int j = 0; j < SIZE; j++)	{
					if (board[i][j] == 0) {
						HashSet<Integer> values = getAvailableValues(i, j);
						if (values.size() == 1) {//found plain solution for place (i, j)
							Integer[] avalues = values.toArray(new Integer[0]);
							board[i][j] = avalues[0];
							found = true;
							break;
						}
					}
				}
			}
		} while (found);

//find recursive solution
		String saved_board = toString();

		for (int i = 0; i < SIZE; i++)	{
			for (int j = 0; j < SIZE; j++)	{
				if (board[i][j] == 0) {
					HashSet<Integer> values = getAvailableValues(i, j);
					Integer[] avalues = values.toArray(new Integer[0]);
					int n_avalues = avalues.length;
					if (values.size() == 0)
						return false;
					if (values.size() > 1) {
						for (int l = 0; l < n_avalues; l++) {
							int k = random.nextInt(n_avalues);//k in 0..n_avalues-1 range
							int temp = avalues[k];
							avalues[k] = avalues[l];
							avalues[l] = temp;
						}
					}

					for (int k = 0; k < n_avalues; k++) {
						board[i][j] = avalues[k];
						if (generate(random)) {//board solved?
							return true;
						} else parseBoard(saved_board);//restore board
					}
					return false;
				}
			}
		}
		
		return true;
	}

//solve: try to solve the sudoku board
	public boolean solve() {
		return solve(0);
	}

	private boolean solve(int level) {
		int min_options = Integer.MAX_VALUE;
		int best_i = -1;
		int best_j = -1;
		HashSet<Integer> maxForbidden = null;

		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				if (board[i][j] == 0) {
					HashSet<Integer> values = getForbiddenValues(i, j);
					if (values.size() == SIZE) return false; 
					int n_options = SIZE - values.size();
					
					if (n_options < min_options) {//MRV heuristic
						min_options = n_options;
						best_i = i;
						best_j = j;
						maxForbidden = values;
					}
					if (min_options == 1) break;
				}
			}
			if (min_options == 1) break;
		}
		if (best_i == -1) return true;

//find recursive solution
		for (int k = 1; k < 10; k++) {
			if (!maxForbidden.contains(k)) {
				board[best_i][best_j] = k;
				if (solve(level + 1))//board solved?
					return true;
				board[best_i][best_j] = 0;//restore board
			}
		}
		return false;
	}

//solveBM: try to solve the sudoku board using bitmap for speed improvement
	public boolean solveBM() {
		return solveBM(0);
	}

	private boolean solveBM(int level) {
		int min_options = Integer.MAX_VALUE;
		int best_i = -1;
		int best_j = -1;
		int maxForbiddenBM = 0;

		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				if (board[i][j] == 0) {
					int values = getForbiddenValuesBM(i, j);
					int n_options = SIZE - Integer.bitCount(values);
					if (n_options == 0) return false; 
					
					if (n_options < min_options) {//MRV heuristic
						min_options = n_options;
						best_i = i;
						best_j = j;
						maxForbiddenBM = values;
					}
					if (min_options == 1) break;
				}
			}
			if (min_options == 1) break;
		}
		if (best_i == -1) return true;

//find recursive solution
		String saved_board = toString();
		for (int k = 1; k < 10; k++) {
			if ((maxForbiddenBM & value_mask[k]) == 0) {
				board[best_i][best_j] = k;
				if (solveBM(level + 1)) //board solved?
					return true;
				board[best_i][best_j] = 0;//restore board
			}
		}
		return false;
	}

//fastsolveBM: try to solve the sudoku board using bitmap and forbidden tables for speed improvement
	public boolean fastsolveBM() {
		return fastsolveBM(0);
	}

	private boolean fastsolveBM(int level) {
		int[] rows_forbidden = new int[SIZE];//row values forbidden by Sudoku rules
		for (int i = 0; i < SIZE; i++)	{
			int values = 0;
			for (int j = 0; j < SIZE; j++)	{
				if (board[i][j] != 0)
					values |= value_mask[board[i][j]];
			}
			rows_forbidden[i] = values;
		}
		int[] column_forbidden = new int[SIZE];//column values forbidden by Sudoku rules
		for (int j = 0; j < SIZE; j++)	{
			int values = 0;
			for (int i = 0; i < SIZE; i++)	{
				if (board[i][j] != 0)
					values |= value_mask[board[i][j]];
			}
			column_forbidden[j] = values;
		}
		int[][] square_forbidden = new int[BLOCK_SIZE][BLOCK_SIZE];//square values forbidden by Sudoku rules
		for (int p = 0; p < BLOCK_SIZE; p++)	{
			int ii = p * BLOCK_SIZE;
			for (int q = 0; q < BLOCK_SIZE; q++)	{
				int jj = q * BLOCK_SIZE;
				int values = 0;
				for (int i = ii; i < ii + BLOCK_SIZE; i++)	{
					for (int j = jj; j < jj + BLOCK_SIZE; j++)	{
						if (board[i][j] != 0)
							values |= value_mask[board[i][j]];
					}
				}
				square_forbidden[p][q] = values;
			}
		}

		return fastsolveBM(level, rows_forbidden, column_forbidden, square_forbidden);
	}
	private boolean fastsolveBM(int level, int[] rows_forbidden, int[] column_forbidden, int[][] square_forbidden) {
		int minOptions = 10;
		int best_i = -1;
		int best_j = -1;
		int maxForbiddenBM = 0;

		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				if (board[i][j] == 0) {
					int forbidden = rows_forbidden[i] | column_forbidden[j] | square_forbidden[i / BLOCK_SIZE][j / BLOCK_SIZE];
					int n_options = SIZE - Integer.bitCount(forbidden);

					if (n_options == 0) return false;

					if (n_options < minOptions) {//MRV heuristic
						minOptions = n_options;
						best_i = i;
						best_j = j;
						maxForbiddenBM = forbidden;
					}
					if (minOptions == 1) break;
				}
			}
			if (minOptions == 1) break;
		}
		if (best_i == -1) return true;

//find recursive solution
		for (int k = 1; k < 10; k++) {
			if ((maxForbiddenBM & value_mask[k]) == 0) {
				board[best_i][best_j] = k;
				int vmask = value_mask[k];
				rows_forbidden[best_i] |= vmask;
				column_forbidden[best_j] |= vmask;
				square_forbidden[best_i / BLOCK_SIZE][best_j / BLOCK_SIZE] |= vmask;

				if (fastsolveBM(level + 1, rows_forbidden, column_forbidden, square_forbidden))//board solved?
					return true;

				board[best_i][best_j] = 0;//restore board
				rows_forbidden[best_i] &= ~vmask;
				column_forbidden[best_j] &= ~vmask;
				square_forbidden[best_i / BLOCK_SIZE][best_j / BLOCK_SIZE] &= ~vmask;
			}
		}
		return false;
	}

//solveDLX: try to solve the sudoku board using DLX Sudoku solver developed by Shivan Kaul Sahib, reference: https://github.com/ShivanKaul/Sudoku-DLX/tree/master
	public boolean solveDLX() {
		return solveDLX(0);
	}
	private boolean solveDLX(int level) {
		SudokuDLX s = new SudokuDLX(3);
		s.parseBoard(toString());
		if (s.solve()) {
			parseBoard(s.toString());//import result
			return true;
		} else return false;
	}

//getNumberOfValues: get Number Of Values > 0
	public int getNumberOfValues() {
		int counter = 0;
		for (int i = 0; i < SIZE; i++)	{
			for (int j = 0; j < SIZE; j++)	{
				if (board[i][j] > 0)
					counter++;
			}
		}
		return counter;
	}

//isFull: check if board is full
	public boolean isFull() {
		for (int i = 0; i < SIZE; i++)	{
			for (int j = 0; j < SIZE; j++)	{
				if (board[i][j] == 0)
					return false;
			}
		}
		return true;
	}

//isCorrect: check if board is correct
	public boolean isCorrect() {
//check rows
		for (int i = 0; i < SIZE; i++)	{
			HashSet<Integer> values = new HashSet<>();
			for (int j = 0; j < SIZE; j++)	{
				if (board[i][j] > 0 && values.contains(board[i][j]))
					return false;
				values.add(board[i][j]);
			}
		}
//check columns
		for (int j = 0; j < SIZE; j++)	{
			HashSet<Integer> values = new HashSet<>();
			for (int i = 0; i < SIZE; i++)	{
				if (board[i][j] > 0 && values.contains(board[i][j]))
					return false;
				values.add(board[i][j]);
			}
		}
//check squares
		for (int p = 0; p < SIZE; p += BLOCK_SIZE)	{
			for (int q = 0; q < SIZE; q += BLOCK_SIZE)	{
				HashSet<Integer> values = new HashSet<>();
				for (int i = p; i < p + BLOCK_SIZE; i++)	{
					for (int j = q; j < q + BLOCK_SIZE; j++)	{
						if (board[i][j] > 0 && values.contains(board[i][j]))
							return false;
						values.add(board[i][j]);
					}
				}
			}
		}
		return true;
	}

//cloneBoard(): get board as int[][] using deep copy
    public int[][] cloneBoard() {
		int[][] copy = new int[board.length][];
		for (int i = 0; i < board.length; i++) {
			copy[i] = board[i].clone(); 
		}
		return copy;
	}

//toString: pack board as string
    public String toString() {
		StringBuilder sb = new StringBuilder(TOTAL_CELLS);
		for (int i = 0; i < SIZE; i++)	{
			for (int j = 0; j < SIZE; j++)	{
				sb.append(board[i][j]);
			}
		}
		return sb.toString();
	}

//parseBoard: unpack string to board
    public boolean parseBoard(String str) {
		for (int i = 0; i < SIZE; i++)	{
			for (int j = 0; j < SIZE; j++)	{
				char ch = str.charAt(i * SIZE + j);
				if (ch >= '0' && ch <= '9')
					board[i][j] = ch - '0';
				else if (ch == '.')
					board[i][j] = 0;
				else return false;//stop parsing, return false
			}
		}
		return true;
	}

//print: print content of board on console output
    public void print(boolean nice) {
		for (int i = 0; i < SIZE; i++)	{
			if (nice && i > 0 && i % BLOCK_SIZE == 0)
				System.out.println("---+---+---");
			for (int j = 0; j < SIZE; j++)	{
				if (nice && j > 0 && j % BLOCK_SIZE == 0)
					System.out.print('|');
				System.out.print(board[i][j]);
			}
			System.out.println();
		}
	}

//internal function, it finds values related to place (ii, jj), used by solve()
	private HashSet<Integer> getForbiddenValues(int ii, int jj) {
		HashSet<Integer> values = new HashSet<>();
//check rows
		for (int i = 0; i < SIZE; i++)	{
			if (board[i][jj] > 0)
				values.add(board[i][jj]);
		}
//check columns
		for (int j = 0; j < SIZE; j++)	{
			if (board[ii][j] > 0)
				values.add(board[ii][j]);
		}
//check square
		int p = (ii / BLOCK_SIZE) * BLOCK_SIZE;
		int q = (jj / BLOCK_SIZE) * BLOCK_SIZE;
		for (int i = p; i < p + BLOCK_SIZE; i++)	{
			for (int j = q; j < q + BLOCK_SIZE; j++)	{
				if (board[i][j] > 0)
					values.add(board[i][j]);
			}
		}
		return values;
	}

//internal function, it finds values available for place (ii, jj), used by generate()
	private HashSet<Integer> getAvailableValues(int ii, int jj) {
		HashSet<Integer> values = new HashSet<>();
		for (int k = 1; k < 10; k++) values.add(k);//all values 1..9

//check rows
		for (int i = 0; i < SIZE; i++)	{
			if (board[i][jj] > 0)
				values.remove(board[i][jj]);
		}
//check columns
		for (int j = 0; j < SIZE; j++)	{
			if (board[ii][j] > 0)
				values.remove(board[ii][j]);
		}
//check square
		int p = (ii / BLOCK_SIZE) * BLOCK_SIZE;
		int q = (jj / BLOCK_SIZE) * BLOCK_SIZE;
		for (int i = p; i < p + BLOCK_SIZE; i++)	{
			for (int j = q; j < q + BLOCK_SIZE; j++)	{
				if (board[i][j] > 0)
					values.remove(board[i][j]);
			}
		}
		return values;
	}

//internal function, it finds values related to place (ii, jj) using bitmap, used by solveBM()
	private int getForbiddenValuesBM(int ii, int jj) {
		int values = 0;
//check rows
		for (int i = 0; i < SIZE; i++)	{
			if (board[i][jj] > 0)
				values |= value_mask[board[i][jj]];
		}
//check columns
		for (int j = 0; j < SIZE; j++)	{
			if (board[ii][j] > 0)
				values |= value_mask[board[ii][j]];
		}
//check square
		int p = (ii / BLOCK_SIZE) * BLOCK_SIZE;
		int q = (jj / BLOCK_SIZE) * BLOCK_SIZE;
		for (int i = p; i < p + BLOCK_SIZE; i++)	{
			for (int j = q; j < q + BLOCK_SIZE; j++)	{
				if (board[i][j] > 0)
					values |= value_mask[board[i][j]];
			}
		}
		return values;
	}


//uniqueSolutionExist(): check that one and only one solution exists
	public boolean uniqueSolutionExist() {
		int[] rows_f = new int[SIZE];
		int[] cols_f = new int[SIZE];
		int[][] sqs_f = new int[BLOCK_SIZE][BLOCK_SIZE];

		// Initialize masks
		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				if (board[i][j] != 0) {
					int vmask = value_mask[board[i][j]];
					rows_f[i] |= vmask;
					cols_f[j] |= vmask;
					sqs_f[i / BLOCK_SIZE][j / BLOCK_SIZE] |= vmask;
				}
			}
		}
		return countSolutionsRecursive(rows_f, cols_f, sqs_f) == 1;
	}

	private int countSolutionsRecursive(int[] rows_f, int[] cols_f, int[][] sqs_f) {
		int minOptions = 10;
		int best_i = -1;
		int best_j = -1;
		int maxForbiddenBM = 0;

		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				if (board[i][j] == 0) {
					int forbidden = rows_f[i] | cols_f[j] | sqs_f[i / BLOCK_SIZE][j / BLOCK_SIZE];
					int n_options = SIZE - Integer.bitCount(forbidden);

					if (n_options == 0) return 0;

					if (n_options < minOptions) {//MRV heuristic
						minOptions = n_options;
						best_i = i;
						best_j = j;
						maxForbiddenBM = forbidden;
					}
					if (minOptions == 1) break;
				}
			}
			if (minOptions == 1) break;
		}

		if (best_i == -1) return 1;

		int totalFound = 0;
//find recursive solution
		for (int k = 1; k <= SIZE; k++) {
			if ((maxForbiddenBM & value_mask[k]) == 0) {
				board[best_i][best_j] = k;
				int vbit = value_mask[k];
				rows_f[best_i] |= vbit;
				cols_f[best_j] |= vbit;
				sqs_f[best_i / BLOCK_SIZE][best_j / BLOCK_SIZE] |= vbit;

				totalFound += countSolutionsRecursive(rows_f, cols_f, sqs_f);

				board[best_i][best_j] = 0;//restore board
				rows_f[best_i] &= ~vbit;
				cols_f[best_j] &= ~vbit;
				sqs_f[best_i / BLOCK_SIZE][best_j / BLOCK_SIZE] &= ~vbit;

				if (totalFound >= 2) return totalFound;//fast-exit
			}
		}
		return totalFound;
	}

	private final static int REPEAT_COUNT = 10;
	public void doBenchmark(Function<Integer, Boolean> solver) {
		long t0 = System.nanoTime(); long total = 0;
		long max = 0; int slowest_run = 0;
		for (int k = 0; k < REPEAT_COUNT; k++) {
			for (int i = 0; i < benchmarks.length; i++) {
				parseBoard(benchmarks[i]);
				solver.apply(0);
				long t = System.nanoTime();
				long delta = t - t0;
				t0 = t; total += delta;
				if (delta > max) {
					max = delta;
					slowest_run = i;
				}
				if (i % REPEAT_COUNT == 0)
					System.out.print(".");
			}
		}
		System.out.println();
		System.out.println("Average solver time: " + total / 1e6 / benchmarks.length / REPEAT_COUNT + " ms");
		System.out.println("Max solver time: " + max / 1e6 + " ms, running benchmark " + benchmarks[slowest_run]);
	}

	final static String[] benchmarks = {//reference boards for benchmarking
		".........6..4..1...47..2.6.8.6........93..8.2....84..7...7...863...267..........1", 
		"........6.....8....1...9..823.9.....1.68.7.3.....2.46.6....3.2..2........417...9.", 
		".......12........3..23..4....18....5.6..7.8.......9.....85.....9...4.5..47...6...", 
		".......3.742...5..5...4.67.9........384..6....6...3.....6.........4.7...8..6.9.25", 
		"......2...52.1..638..35..97......4....5..1.8.......956.2...8..9....2..........52.", 
		"......31.....4.....198234....53.2......7...63..76.1..........85..6........1..8..6", 
		"......367......2...65..38..92135.6....7...92.....9........6...14.2.3...........3.", 
		"......9..1..8.......5....1...1....46..3..........2...539....421...39156....6.23..", 
		".....1..8..4.....3.9..84.7236...8..5.18.........639..........37.3....1.......3.4.", 
		".....2791.17..8.......17..............1...5.7..8.....2..32.485.8...76......38....", 
		".....3.2..9....53.....9..4....8.....2.9.4........6..9.9...2.1....5..4.723.2...98.", 
		".....38...986.2.34..3..8............6........81..4659.9.....24..4.......3.6.2....", 
		".....85..1....4.8.7....5...6..........24....5..4.6...8.5...7...4....1.9..1.946.5.", 
		"....4......3.8..514.5........9.....8.5...8.433.7.1...5.3..2......4..9....9..7.6..", 
		"....4..5..8.51..3...3..6.7...7......538.....6........38.1..4.9...4.....5....29..4", 
		"....4..91.1..2.4.......628.7......32....8.....2..7......2...37.374.6......9..7...", 
		"....6.3..9...2...1.741.32..1693.5...7.....163...6.7..............6..9.....7......", 
		"....6.5...8.....4..1478.39..6..48.....8......5....1...85.....7..768.9...........6", 
		"....63..1.....8..96.......89......8.4.1.5.9...7..9....1...4...5..6.2...4..47....6", 
		"....92..86.47.5...9...8..6....4......2....3...1.9...8.8.1.....6.39...21.........9", 
		"...1...5.9..4..3...7.....8...2.9...4.8.....6.5...3.7...6.....2...1..7..3.5...4...", 
		"...1...89..2.5...6..3......6.1...87...........5.4....39.53..761..4......3.8...9..", 
		"...2...1..64.....3....6......2.7........2.39.485...2....8....32.396.2.....1.....9", 
		"...2...6.8..5..9...1.....7...3.8...5.9.....2.6...4.1...7.....3...2..4..9.6...5...", 
		"...2...6.9..5..3...7.....2...3.8.1...6.....5...1.9.4...8.....1...9..3..7.6...4...", 
		"...2..16.2....4.7..6.8.1.....2......679..385.1...8.2...1.....8.4......36.........", 
		"...2.5....496...5.......12..3.95.....54...739...37....4......72......5......8..9.", 
		"...25...12.3...5..5.9...4.......17........8...4.5.7.19...9...8..8.1.....9.7..8...", 
		"...3.......4.........9846...53..8.......36...4..59.....467.51.3.9..4....7......4.", 
		"...3...7.2..9..5...6.....1...4.2...9.8.....3.1...5.8...7.....6...4..1..5.8...2...", 
		"...4...8.3..7..6...5.....2...9.3...7.2.....5.8...1.6...4.....9...2..8..1.6...7...", 
		"...5...9.4..8..7...2.....3...6.4...8.3.....1.9...7.2...1.....6...5..2..7.9...1...", 
		"...56...9..4......6....4...........23.9.52.....7.1...89.....71.....4..2.8.32.1.4.", 
		"...6...1.5..9..8...3.....4...7.5...9.4.....2.1...8.3...2.....7...6..3..8.1...4...", 
		"...7...2.6..1..9...4.....5...8.6...1.5.....3.2...9.4...3.....8...7..4..9.2...1...", 
		"...8...3.7..2..1...5.....6...9.7...2.6.....4.3...1.5...4.....9...8..5..1.3...2...", 
		"...8..2....2....95...5....3.4..9.....9......22.....87.726...3.8......9.198.6.....", 
		"...8..617.761.2.94..9........8....23.9....4...3........63......2..76....9.......5", 
		"...9...4.8..3..2...6.....7...1.8...3.7.....5.4...2.6...5.....1...9..6..2.4...3...", 
		"..1.....92..5..7..4....3..8.6.....5.5.....3.2.....7.9..4....1..7..5..28.....6....", 
		"..1...5..2..8.6...4.7.....9...3.1...6.....9...7.4...3.....2.6...9.8..1..4...7....", 
		"..1.75......93.7...9.1.2.461.4....6.....43..2.36.............1..1...4..9......8..", 
		"..1.78.....9.3.7..........4.75......6..4..1.9..........93...4.54...9...6.5.34...7", 
		"..2.....18..6..9..5....4..7.9.....3.3.....1.8.....2.4..7....6..9..3..17.....8....", 
		"..2...6..3..9.7...5.8.....1...4.2...7.....1...8.5...4.....9.7...1.3..2..6...8....", 
		"..3.....27..7..1..6....5..9.1.....8.8.....4.3.....6.5..9....2..1..8..39.....4....", 
		"..3...7..4..1.8...6.9.....2...5.3...8.....2...9.6...5.....1.8...2.4..3..7...9....", 
		"..38...4..........6.1....8.368..54....528..........8...7....2.8.........1849.3.7.", 
		"..4.....3.396....4..74...2.....73....73.......1.......3...6...284..9.7..7..1....5", 
		"..4.....36..8..2..7....9..1.2.....5.5.....7.4.....8.9..1....3..2..7..65.....9....", 
		"..4...6..5..2.9...7.1.....3...8.4...9.....2...5.7...6.....1.8...3.5..2..9...7....", 
		"..5.....41..9..3..8....7..2.7.....6.6.....9.5.....2.3..8....4..3..9..17.....2....", 
		"..5...9..6..3.1...8.2.....4...7.5...1.....6...9.8...7.....3.1...4.6..5..8...2....", 
		"..58..69......17....452.38.....4.9..94........3.7..82.....9..5.1......3.......2..", 
		"..6........2.9.7....3.....5.....48.9...5..........814..2938.........139831.9.....", 
		"..6.....59..1..4..3....8..7.4.....2.2.....5.9.....3.8..7....1..4..5..92.....3....", 
		"..6...1..7..4.2...9.3.....5...8.6...2.....7...1.9...8.....4.2...5.7..6..9...3....", 
		"..6...17.....5.3.......1.......98.4.7.84....1.5............3...4.3..2..79275..8..", 
		"..6..85............4..759....15..43...7..96...9.......7...42..13......94..4..6...", 
		"..7.....28..3..5..9....1..6.5.....4.4.....2.8.....6.1..9....5..5..7..39.....1....", 
		"..7.....515.63....8..............6.....2.3..9..28.6...59....2..27.....3.41....5.8", 
		"..7.....86..5..9..3....1..4.9.....2.2.....8.5.....3.6..8....5..7..4..39.....2....", 
		"..7...2..8..5.3...1.4.....6...9.7...3.....8...2.1...9.....5.3...6.8..7..1...4....", 
		"..8.....47..3..6..9....2..5.3.....1.1.....7.4.....9.2..5....8..6..1..75.....3....", 
		"..8...3..9..6.4...2.5.....7...1.8...4.....9...3.2...1.....6.4...7.9..8..2...5....", 
		"..9.....35..4..8..2....1..6.8.....4.4.....2.7.....5.1..3....9..8..2..46.....7....", 
		"..9...4..1..7.5...3.6.....8...2.9...5.....7...4.3...2.....1.5...8.6..9..3...7....", 
		"..9...7..1..3.8...6.4.....2...5.9...8.....1...7.6...3.....8.5...4.1..9..2...6....", 
		".1...5..7672......9.5.7....8...5..1.3....4.92.....3......5...8...9......5...18..9", 
		".1..4....7....9.1..3.6...8...5..3...9.....2...8..5...6...2.3..5.8....4....2..7...", 
		".1..4....8....2.1..3.7...5...6..3...9.....7...5..6...4...1.3..7.8....9....2..6...", 
		".1.2.7.9...5.4.67.69.....2.....7..............41..9..7......1.2...35.7......1.3.6", 
		".1.5.86..5..791......2.........8......3..92.......7....6.87...51.4.6..9....9..3..", 
		".1.78.62......1..3.2..5.....8......4.....4..66......5.....28..7.......4.9.24.3.6.", 
		".2..5....8....1.2..4.7...9...3..4...1.....6...9..3...7...8.4..6.9....1....5..8...", 
		".2..5....9....3.2..4.8...6...7..4...1.....8...6..7...5...2.4..8.9....1....3..7...", 
		".2.3..4.......2...95......2.9.....2.......8..4....5.6..47.59..3...6.3.7.....28.4.", 
		".2.5.7....96.......1.8..4..8......2...9..4..6.4...1...9...1..8.2...6.......4..963", 
		".2.91...3..7.52.6...6......41....8.58............4......5..1..92.1.7.6..6.......1", 
		".27....8.....4..17.....5.6....513..4..3......5.2........4.89..........4.1..42..59", 
		".3....478.8..23.5.6751...9...9.5...2......1.4....1..............1...9......23.8..", 
		".3..6....1....4.3..5.9...7...8..5...2.....1...7..8...9...6.5..7.1....2....4..9...", 
		".3.7...6.........98.2..1...987........5....81....9.7.251...........17...2.6.3.5..", 
		".3.8........7..9.6..2.4.....98..........85.91....6.8...1..7936....41.........6..9", 
		".35.417.8......4.5.215........7..2.9.1..........45......9.24...35....1.2.........", 
		".39..415......3...65...1...9..14...........61.2.69....7.........9..1...5.85.3....", 
		".4...7.1.......8....7..5..4.8...21..1.2....8..94.......59.1.........67..76..4..5.", 
		".4..7....2....5.4..6.1...8...9..6...3.....5...8..9...1...7.6..8.2....3....6..1...", 
		".5..8....3....6.5..7.2...9...1..7...4.....3...9..1...2...8.7..1.3....4....5..2...", 
		".5..8....6....1.8..7.2...3...1..7...4.....9...8..5...9...4.5..2.8....9....1..7...", 
		".5.2.1.842....5...98.6...2.....1....8......97.......4..7.1..5..5.....13..2.....7.", 
		".5.7..12...1....3..........9..1.4...234.7..8......27.3...3.....5.84..3.2........8", 
		".5.967...9..42.67..4..1...58.....4..471......3..6.....1..7..5........7........1..", 
		".6.....3...83.6.5....2.8...2..85..6935.67......64.......9.3......4....9.5........", 
		".6..9....4....7.6..8.3...1...2..8...5.....4...1..2...3...9.8..2.4....5....7..3...", 
		".6..9....8....4.6..2.3...1...7..2...5.....8...3..6...4...1.3..9.2....5....8..4...", 
		".6.7...5...............93....791.5....26......3..8.7...9.15.6..6..29..3.1.......4", 
		".7..1....5....8.7..9.4...2...3..9...6.....2...8..3...4...1.9..3.5....6....8..4...", 
		".7..2....9....3.7..1.8...4...6..1...3.....9...4..8...2...5.1..8.4....6....9..5...", 
		".7.219..5..8..7......4.83.........736....3.....7...259...9.1.....1..2......3..5..", 
		".8..1....3....6.8..9.5...2...4..9...7.....1...2..7...5...8.4..1.7....3....5..9...", 
		".8..2....6....9.8..1.5...3...4..1...7.....5...3..4...2...8.1..5.6....7....9..2...", 
		".9........5....7.87...5.2.9..14...9..6.9.....4...83...........1..75.1.2.6.3....4.", 
		".9....7.54.......2.3.2.....2..76..9..65...........3...5....192......4.836..8....1", 
		".9..3....4....7.9..8.1...5...2..8...6.....4...5..2...7...9.6..4.2....5....7..1...", 
		".9..3....7....1.9..2.6...4...5..2...8.....6...4..5...3...9.2..6.7....8....1..5...", 
		".9387......1.5.......32...9.7.......6....53.1...7.....2.5.67.......18.56....9....", 
		".94......78..1.....5.....7........9152..7.....1.6...82.3..5.9.7.4.83....2........", 
		"1...5...9.4.......6.8.3...7...6.4...2.....8...5.3...4...7.1.2.......9.3...8...5..", 
		"12...9.....8..2...5.9..8...3....46..6...7.3.8....3.....8....2..2..4...1..7...3..5", 
		"12..5...464....5.8..............4.219..2..8......8.6....1...2........7..76.12.4..", 
		"16.....5...7...8.2.542..1..2.....561..........1.8..3.....45..........41.435......", 
		"2....5.8.1..8..6..6...3........5.8.2.5.7...6......8......5...9..96.4..28...2..3..", 
		"2...6...8.5.......7.9.4...1...7.5...3.....9...6.2...8...3.7.4.......5.1...9...3..", 
		"2...6.7..1....4.......3.2.....7..5.2.8.4.2........5...3728.6....9.........5.49.7.", 
		"2.4..7563..5....74...4.5..1...5....2....82.......4....3....91....9.......8.....59", 
		"2.8.3..7..69..........69..4.3..5...2.5..1....924.8............14.3............693", 
		"24.1..6....67.45.........1.35....74.7.8......6.4.....98...7....4.....9.7......8..", 
		"258....16..9.2.34....8.9275.......5.14.....................3..7...6..4..4.1....9.", 
		"26....7.....5.61...5...7..63.9........86..3...26........2...6.9...8.....4..2.38..", 
		"3....17.2.2.....18..1.....5.....2....1..4..6.....8...12..1..58.....7.1.9.4...8...", 
		"3....21968...35....9............695...6.......3.4..........4..85.......9289..7..4", 
		"3...7...1.6.......8.5.2...4...8.6...5.....7...2.4...9...1.5.3.......6.4...2...8..", 
		"3..5..8....8.3...........5.......9...1....4......4.5..7831...4.6...85...15.364...", 
		"3.2.7.5.1...8...6.7.....3.4.....184..6......5....2.9....36.7..9............3.21..", 
		"3.45..9.25.9.3.............41......3782...4.9........8..3.............37....64.91", 
		"3.75.6........9.....67......1...2.9...2.......3....52.2..6..9..9.12.....65..9.2..", 
		"31..5.2.747......9......34.9..2....3.8.....5..3.....7.7.3.6.......72......6.....5", 
		"354..1.7......5..46..4.....4...1.............8.5.4.291.....6..2.469.2....2.......", 
		"4...1...8........71.5....397....8.9..58...716.....2..58.....973...8.............1", 
		"4...8...2.7.......9.6.5...3...9.7...6.....8...3.1...5...4.6.2.......7.5...1...9..", 
		"49..32.6.8.7.4.3.1.62.57...1...2.....25..4.3.7...16........3.....................", 
		"4936...7.68..4..5............6.5.......96.3.....13....8.4...93.....2.5......9...1", 
		"5..........3..27.4.6..93...47....6......15.7...2.4.198........1..........16....37", 
		"5...8..............32......18..2....3...9.1..45.81.9.2.45.3.......64.25......1...", 
		"5...9...3.8.......1.7.6...2...1.8...7.....4...6.2...1...5.7.3.......8.6...4...2..", 
		"5.7.2......6.5....2..7..5....4..3.1...3......6...7..3.365.92.....2.....9.49......", 
		"53..7.......195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79", 
		"547...2.1....54..7.6.7.2.....8.....5........2.5..768..1.5.....8.......5.3.6......", 
		"563....1.....5....4.....5...4.....6..9..7.3..25643..89..59......3....9..9........", 
		"6...1...4.9.......2.8.7...5...2.9...8.....3...7.5...2...6.8.4.......9.7...3...1..", 
		"6.2..4.3......98.28.42....9........634............3..4.....8...9..1.74..4.3....5.", 
		"6.4.57.1.8...6...45.7.......5.....3...23..............29..1.3.8....9...1.8.63....", 
		"615..8..79..4...8..4.5......2...74.....1.......1.5.978.....2..9.......6....6...3.", 
		"7...2...5.1.......3.9.8...6...3.1...9.....5...8.6...7...4.9.2.......1.8...5...4..", 
		"7..6.9823...2..4....9.....1..6.9.........75.8....5.....8.9...5...438..7.......3..", 
		"7.1.......5....2...43.5...6..9.3.4..4.5...1.88...1......7.4.6.9..2.....4........2", 
		"7.6......4..........1..9...6..9.28....2...63...53..42..9......4..3.941....8....9.", 
		"8...3...6.2.......5.1.9...7...4.2...1.....6...9.7...4...8.1.5.......2.9...6...3..", 
		"9......7..28.....6.7...8.1..5..42.....2......8.....1......7..61....81..7.1.9...85", 
		"9.....2...6.395.4.....6....18.24.......5..1......13...62.....1..1...7..3.7.9.....", 
		"9...4...7.3.......6.2.5...8...6.3...2.....9...5.8...1...9.2.4.......3.8...1...6..", 
		"9.4.7.1....1.9......6....2..7.1...598...2.4..1.......63...87.....7....9.6......1.", 
		"9.5.6.....7.1..6.....5...34..3..........395...9...13...2..1..53...........19..7.6", 
		"9.7......65...83...........73...5....15..97.....3..6515....3.7..........1.29...4.", 
		"937..65.......3...2.6......7.......9..4..9...59..7..464.....3..3......9...8...45."};

//demo	
	public static void main(String[] args) {
		if (args.length == 0) {
//forever test for solver()
			Sudoku sudo = new Sudoku();
			while (true) {
				sudo.generate(30);
				sudo.fastsolveBM(0);
				if (sudo.isFull() && sudo.isCorrect()) {
					System.out.print(".");
				} else System.out.println("#values = " + sudo.getNumberOfValues() + ", isFull = " + sudo.isFull() + ", isCorrect = " + sudo.isCorrect());
			}
		} else if (args[0].equals("-benchmark")) {
			Sudoku sudo = new Sudoku();
			System.out.println("Benchmarking solve()");
			sudo.doBenchmark(sudo::solve);
			System.out.println("----------------------");
			System.out.println("Benchmarking solveBM()");
			sudo.doBenchmark(sudo::solveBM);
			System.out.println("----------------------");
			System.out.println("Benchmarking fastsolveBM()");
			sudo.doBenchmark(sudo::fastsolveBM);
			System.out.println("----------------------");
			System.out.println("Benchmarking solveDLX()");
			sudo.doBenchmark(sudo::solveDLX);
		} else {
			String board = args[0];
			if (board.length() == TOTAL_CELLS) {
				Sudoku sudo = new Sudoku();
				if (sudo.parseBoard(board) && sudo.isCorrect()) {
					sudo.print(true);
					System.out.println("#values = " + sudo.getNumberOfValues() + ", isFull = " + sudo.isFull() + ", isCorrect = " + sudo.isCorrect());
					long t0 = System.nanoTime();
					if (sudo.fastsolveBM(0)) {
						System.out.println("Solution found in " + (System.nanoTime() - t0) / 1e6 + " ms:");
						sudo.print(true);
						System.out.println("#values = " + sudo.getNumberOfValues() + ", isFull = " + sudo.isFull() + ", isCorrect = " + sudo.isCorrect());
					} else System.out.println("No solution found");//not all sudoku boards have solution, e.g.: 000000012000000003002300400001800005060070800000009000008500000900040500470006001
				} else System.out.println("Incorrect string, please double check: " + board);
			} else System.out.println("Usage: java Sudoku [<board>]\n<board>: 81 characters string defining a Sudoku board");
		}
    }

}
