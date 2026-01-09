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

Usage: java solver.Sudoku [-benchmark | -check | <board>]

without parameters: it runs random tests forever
with parameters:
-benchmark: performs benchmark using a specific set of boards
-check: check that all solvers return same result for a specific set of boards
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
							board[i][j] = values.iterator().next();
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
					if (values.isEmpty())
						return false;
					if (values.size() > 1) {
						for (int l = 0; l < n_avalues; l++) {
							int k = random.nextInt(n_avalues);//k in 0..n_avalues-1 range
							int temp = avalues[k];
							avalues[k] = avalues[l];
							avalues[l] = temp;
						}
					}

                    for (Integer avalue : avalues) {
                        board[i][j] = avalue;
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

	private static final int SIZE_PLUS_1 = SIZE + 1;
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
		for (int k = 1; k < SIZE_PLUS_1; k++) {
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
		for (int k = 1; k < SIZE_PLUS_1; k++) {
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
		int minOptions = Integer.MAX_VALUE;
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
		for (int k = 1; k < SIZE_PLUS_1; k++) {
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
		return isCorrect();
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
		for (int i = p; i < p + BLOCK_SIZE; i++) {
			for (int j = q; j < q + BLOCK_SIZE; j++) {
				if (board[i][j] > 0)
					values.add(board[i][j]);
			}
		}
		return values;
	}

//internal function, it finds values available for place (ii, jj), used by generate()
	private HashSet<Integer> getAvailableValues(int ii, int jj) {
		HashSet<Integer> values = new HashSet<>();
		for (int k = 1; k < SIZE_PLUS_1; k++) values.add(k);//all values 1..9

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
		int min_options = Integer.MAX_VALUE;
		int best_i = -1;
		int best_j = -1;
		int maxForbiddenBM = 0;

		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				if (board[i][j] == 0) {
					int forbidden = rows_f[i] | cols_f[j] | sqs_f[i / BLOCK_SIZE][j / BLOCK_SIZE];
					int n_options = SIZE - Integer.bitCount(forbidden);

					if (n_options == 0) return 0;

					if (n_options < min_options) {//MRV heuristic
						min_options = n_options;
						best_i = i;
						best_j = j;
						maxForbiddenBM = forbidden;
					}
					if (min_options == 1) break;
				}
			}
			if (min_options == 1) break;
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
		long max = 0; int slowest_run = 0, n_runs = 0;
		for (int k = 0; k < REPEAT_COUNT; k++) {
			for (int i = 0; i < Resources.benchmarks.length; i++) {
				if (parseBoard(Resources.benchmarks[i])) {
					if (solver.apply(0)) {
						n_runs++;
						long t = System.nanoTime();
						long delta = t - t0;
						t0 = t; total += delta;
						if (delta > max) {
							max = delta;
							slowest_run = i;
						}
						if (n_runs % REPEAT_COUNT == 0)
							System.out.print(".");
					}
				}
			}
		}
		System.out.println();
		System.out.println("Average solver time: " + total / 1e6 / n_runs + " ms");
		System.out.println("Max solver time: " + max / 1e6 + " ms, running benchmark " + Resources.benchmarks[slowest_run]);
	}

	public void doCheck() {
		int n_tests = 0;
		int n_fails = 0;
		for (int i = 0; i < Resources.benchmarks.length; i++) {
			if (parseBoard(Resources.benchmarks[i])) {
				String res_solve = solve() ? toString() : "no solution";
				String res_solveBM = solveBM() ? toString() : "no solution";
				String res_fastsolveBM = fastsolveBM() ? toString() : "no solution";
				String res_solveDLX = solveDLX() ? toString() : "no solution";
				n_tests++;
				if (!res_solve.equals(res_solveBM) || !res_solveBM.equals(res_fastsolveBM) || !res_fastsolveBM.equals(res_solveDLX)) {
					n_fails++;
					System.out.println("different results found for Sudoku: " + Resources.benchmarks[i]);
					System.out.println(res_solve);
					System.out.println(res_solveBM);
					System.out.println(res_fastsolveBM);
					System.out.println(res_solveDLX);
				}
			} else System.out.println("invalid board: " + Resources.benchmarks[i]);
		}
		System.out.println(n_tests + " tests performed, " + (n_fails == 0 ? "without failure" : "with " + n_fails + " failure(s)"));
	}

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
		} else if (args[0].equals("-check")) {
			Sudoku sudo = new Sudoku();
			sudo.doCheck();
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
