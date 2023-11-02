/**
The class Sudoku contains utilities to generate and solve Sudoku game

Note: ordinary values are in 1..9 range, value 0 is used for a free place

24-10-2023: initial release
02-11-2023: added method solveBM() to solve sudoku using bitmaps for speed improvement

Usage: java Sudoku [<board>]

without parameters: it runs random tests forever
with parameters:
<board>: 81 characters string defining a Sudoku board, e.g. 000000012000000003002300400001800005060070800000009000008500000900040500470006000

debug: java Sudoku 000000012000000003002300400001800005060070800000009000008500000900040500470006001
*/
import java.util.HashSet;
import java.util.Random;

public class Sudoku {

    private final int[][] board = new int[9][9];
    private final int[][] template = {{1,2,3,4,5,6,7,8,9},{4,9,7,8,1,2,5,6,3},{5,8,6,3,9,7,1,4,2},{3,5,4,7,6,9,8,2,1},
		{8,6,9,2,4,1,3,5,7},{7,1,2,5,3,8,4,9,6},{9,7,1,6,8,5,2,3,4},{2,3,5,9,7,4,6,1,8},{6,4,8,1,2,3,9,7,5}};

	final static int[] value_mask = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024};//mask used by method solveBM(): value_mask[k] = 2 ^ k

//fillBoard: fill board with random n_values in 1..81 range 
    public void fillBoard(int n_values) {
		int[] permut = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
		Random random = new Random();
		for (int i = 1; i < 10; i++) {
			int k = random.nextInt(9) + 1;//k in 1..9 range
			int temp = permut[k];
			permut[k] = permut[i];
			permut[i] = temp;
		}
		int counter = n_values;
		int[] shuffle = new int[81];
		for (int i = 0; i < 81; i++)
			shuffle[i] = i;
		for (int i = 0; i < 81; i++) {
			int k = random.nextInt(81);//k in 0..80 range
			int temp = shuffle[k];
			shuffle[k] = shuffle[i];
			shuffle[i] = temp;
		}
		for (int i = 0; i < 9; i++)	{
			for (int j = 0; j < 9; j++)	{
				if (counter > 0 && shuffle[i * 9 + j] < n_values) {
					board[i][j] = permut[template[i][j]];
					counter--;
				} else board[i][j] = 0;
			}
		}
	}

//solve: try to solve the sudoku board
	public boolean solve(int level) {
//find plain solution
		boolean found;
		do {
			found = false;
			for (int i = 0; i < 9; i++)	{
				for (int j = 0; j < 9; j++)	{
					if (board[i][j] == 0) {
						HashSet<Integer> values = getRelatedValues(i, j);
						if (values.size() == 8) {//found plain solution for place (i, j)
							for (int k = 1; k < 10; k++) {
								if (!values.contains(k)) {
									board[i][j] = k;
									found = true;
									break;
								}
							}
						}
					}
				}
			}
		} while (found);

//find recursive solution
		String saved_board = toString();

		for (int i = 0; i < 9; i++)	{
			for (int j = 0; j < 9; j++)	{
				if (board[i][j] == 0) {
					HashSet<Integer> values = getRelatedValues(i, j);
					for (int k = 1; k < 10; k++) {
						if (!values.contains(k)) {
							board[i][j] = k;
							if (solve(level + 1)) {//board solved?
								return true;
							} else parseBoard(saved_board);//restore board
						}
					}
					return false;
				}
			}
		}
		
		return true;
	}
//solveBM: try to solve the sudoku board using bitmap for speed improvement
	public boolean solveBM(int level) {
//find plain solution
		boolean found;
		do {
			found = false;
			for (int i = 0; i < 9; i++)	{
				for (int j = 0; j < 9; j++)	{
					if (board[i][j] == 0) {
						int values = getBMRelatedValues(i, j);
						if (Integer.bitCount(values) == 8) {//found plain solution for place (i, j)
							for (int k = 1; k < 10; k++) {
								if ((values & value_mask[k]) == 0) {
									board[i][j] = k;
									found = true;
									break;
								}
							}
						}
					}
				}
			}
		} while (found);

//find recursive solution
		String saved_board = toString();

		for (int i = 0; i < 9; i++)	{
			for (int j = 0; j < 9; j++)	{
				if (board[i][j] == 0) {
					int values = getBMRelatedValues(i, j);
					for (int k = 1; k < 10; k++) {
						if ((values & value_mask[k]) == 0) {
							board[i][j] = k;
							if (solveBM(level + 1)) {//board solved?
								return true;
							} else parseBoard(saved_board);//restore board
						}
					}
					return false;
				}
			}
		}
		
		return true;
	}


//getNumberOfValues: get Number Of Values > 0
	public int getNumberOfValues() {
		int counter = 0;
		for (int i = 0; i < 9; i++)	{
			for (int j = 0; j < 9; j++)	{
				if (board[i][j] > 0)
					counter++;
			}
		}
		return counter;
	}

//isFull: check if board is full
	public boolean isFull() {
		for (int i = 0; i < 9; i++)	{
			for (int j = 0; j < 9; j++)	{
				if (board[i][j] == 0)
					return false;
			}
		}
		return true;
	}

//isCorrect: check if board is correct
	public boolean isCorrect() {
//check rows
		for (int i = 0; i < 9; i++)	{
			HashSet<Integer> values = new HashSet<>();
			for (int j = 0; j < 9; j++)	{
				if (board[i][j] > 0 && values.contains(board[i][j]))
					return false;
				values.add(board[i][j]);
			}
		}
//check columns
		for (int j = 0; j < 9; j++)	{
			HashSet<Integer> values = new HashSet<>();
			for (int i = 0; i < 9; i++)	{
				if (board[i][j] > 0 && values.contains(board[i][j]))
					return false;
				values.add(board[i][j]);
			}
		}
//check squares
		for (int p = 0; p < 9; p += 3)	{
			for (int q = 0; q < 9; q += 3)	{
				HashSet<Integer> values = new HashSet<>();
				for (int i = p; i < p + 3; i++)	{
					for (int j = q; j < q + 3; j++)	{
						if (board[i][j] > 0 && values.contains(board[i][j]))
							return false;
						values.add(board[i][j]);
					}
				}
			}
		}
		return true;
	}

//toString: pack board as string
    public String toString() {
		StringBuilder sb = new StringBuilder(81);
		for (int i = 0; i < 9; i++)	{
			for (int j = 0; j < 9; j++)	{
				sb.append(board[i][j]);
			}
		}
		return sb.toString();
	}

//parseBoard: unpack string to board
    public boolean parseBoard(String str) {
		for (int i = 0; i < 9; i++)	{
			for (int j = 0; j < 9; j++)	{
				char ch = str.charAt(i * 9 + j);
				if (ch >= '0' && ch <= '9')
					board[i][j] = ch - '0';
				else return false;//stop parsing, return false
			}
		}
		return true;
	}

//print: print content of board on console output
    public void print(boolean nice) {
		for (int i = 0; i < 9; i++)	{
			if (nice && i > 0 && i % 3 == 0)
				System.out.println("---+---+---");
			for (int j = 0; j < 9; j++)	{
				if (nice && j > 0 && j % 3 == 0)
					System.out.print('|');
				System.out.print(board[i][j]);
			}
			System.out.println();
		}
	}

//internal function, it finds values related to place (ii, jj), used by solve()
	private HashSet<Integer> getRelatedValues(int ii, int jj) {
		HashSet<Integer> values = new HashSet<>();
//check rows
		for (int i = 0; i < 9; i++)	{
			if (board[i][jj] > 0)
				values.add(board[i][jj]);
		}
//check columns
		for (int j = 0; j < 9; j++)	{
			if (board[ii][j] > 0)
				values.add(board[ii][j]);
		}
//check square
		int p = (ii / 3) * 3;
		int q = (jj / 3) * 3;
		for (int i = p; i < p + 3; i++)	{
			for (int j = q; j < q + 3; j++)	{
				if (board[i][j] > 0)
					values.add(board[i][j]);
			}
		}
		return values;
	}

//internal function, it finds values related to place (ii, jj) using bitmap, used by solveBM()
	private int getBMRelatedValues(int ii, int jj) {
		int values = 0;
//check rows
		for (int i = 0; i < 9; i++)	{
			if (board[i][jj] > 0)
				values |= value_mask[board[i][jj]];
		}
//check columns
		for (int j = 0; j < 9; j++)	{
			if (board[ii][j] > 0)
				values |= value_mask[board[ii][j]];
		}
//check square
		int p = (ii / 3) * 3;
		int q = (jj / 3) * 3;
		for (int i = p; i < p + 3; i++)	{
			for (int j = q; j < q + 3; j++)	{
				if (board[i][j] > 0)
					values |= value_mask[board[i][j]];
			}
		}
		return values;
	}

//demo	
	public static void main(String[] args) {
		if (args.length == 0) {
//forever test for solver()
			Sudoku sudo = new Sudoku();
			while (true) {
				sudo.fillBoard(30);
				sudo.solve(0);
				if (sudo.isFull() && sudo.isCorrect()) {
					System.out.print(".");
				} else System.out.println("#values = " + sudo.getNumberOfValues() + ", isFull = " + sudo.isFull() + ", isCorrect = " + sudo.isCorrect());
			}
		} else {
			String board = args[0];
			if (board.length() == 81) {
				Sudoku sudo = new Sudoku();
				if (sudo.parseBoard(board) && sudo.isCorrect()) {
					sudo.print(true);
					System.out.println("#values = " + sudo.getNumberOfValues() + ", isFull = " + sudo.isFull() + ", isCorrect = " + sudo.isCorrect());
					long t0 = System.nanoTime();
					if (sudo.solveBM(0)) {
						System.out.println("Solution found in " + (System.nanoTime() - t0) / 1e6 + " ms:");
						sudo.print(true);
						System.out.println("#values = " + sudo.getNumberOfValues() + ", isFull = " + sudo.isFull() + ", isCorrect = " + sudo.isCorrect());
					} else System.out.println("No solution found");//not all sudoku boards have solution, e.g.: 000000012000000003002300400001800005060070800000009000008500000900040500470006001
				} else System.out.println("Incorrect string, please double check: " + board);
			} else System.out.println("Usage: java Sudoku [<board>]\n<board>: 81 characters string defining a Sudoku board");
		}
    }

/* generic example
		Sudoku sudo = new Sudoku();
//		sudo.parseBoard("123456789497812563586397142354769821869241357712538496971685234235974618648123975");//template for fillBoard()
		sudo.parseBoard("000000012000000003002300400001800005060070800000009000008500000900040500470006000");//Sudoku Blonde Platine

//		sudo.fillBoard(30);
		sudo.print(true);
		System.out.println("#values = " + sudo.getNumberOfValues() + ", isFull = " + sudo.isFull() + ", isCorrect = " + sudo.isCorrect());
		sudo.solve(0);
		sudo.print(true);
		System.out.println("#values = " + sudo.getNumberOfValues() + ", isFull = " + sudo.isFull() + ", isCorrect = " + sudo.isCorrect());
*/

}
