# Sudoku
The class Sudoku contains utilities to generate and solve Sudoku games

Note: ordinary values are in 1..9 range, value 0 is used for a free place

# Methods
The following methods are provided to generate and solve Sudoku games:
```
fillBoard(int n_values): fills board with random n_values in 1..81 range;

generate(int n_values): random sudoku board generator with random n_values in 1..81 range;

solve(int level): try to solve the sudoku board;

solveBM(int level): try to solve the sudoku board using bitmap for speed improvement;

fastsolveBM(int level): try to solve the sudoku board using bitmap and forbidden tables for speed improvement

solveDLX(int level): try to solve the sudoku board using DLX Sudoku solver developed by Shivan Kaul Sahib, reference: https://github.com/ShivanKaul/Sudoku-DLX/tree/master

parseBoard(String str): unpack string to board;
```

# Usage of sudoku solver from command line
```
Usage: java -cp classes solver.Sudoku [-benchmark | <board>]

without parameters: it runs random tests forever
with parameters:
-benchmark: performs benchmark using a specific set of boards
<board>: 81 characters string defining a Sudoku board, e.g. 000000012000000003002300400001800005060070800000009000008500000900040500470006000
```

# Running interactive sudoku game
``SudokuGame`` is an interactive java Swing app to play Sudoku game, it uses ``fastsolveBM`` method to solve the game
```
Usage: java -cp classes game.SudokuGame
```
Try ``SudokuGame`` using the browser without downloading anything using the *SnapCode* tool: [SudokuGame via SnapCode](https://reportmill.com/SnapCode/app/#open:https://github.com/javalc6/sudoku.zip#/game/SudokuGame.java)

# Examples

Executing the command ``java -cp classes solver.Sudoku 000000012000000003002300400001800005060070800000009000008500000900040500470006000`` provides the following output:

```
000|000|012
000|000|003
002|300|400
---+---+---
001|800|005
060|070|800
000|009|000
---+---+---
008|500|000
900|040|500
470|006|000
#values = 21, isFull = false, isCorrect = true
Solution found in 173.3481 ms:
839|465|712
146|782|953
752|391|486
---+---+---
391|824|675
564|173|829
287|659|341
---+---+---
628|537|194
913|248|567
475|916|238
#values = 81, isFull = true, isCorrect = true
```
Executing the command ``java -cp classes solver.Sudoku -benchmark`` provides the following output running on CPU AMD Ryzen 7 8845HS:

```
Benchmarking solve()
................................................................................................................................................................
Average solver time: 6.2033619230769235 ms
Max solver time: 129.7548 ms, running benchmark .7..1....5....8.7..9.4...2...3..9...6.....2...8..3...4...1.9..3.5....6....8..4...
----------------------
Benchmarking solveBM()
................................................................................................................................................................
Average solver time: 2.890679230769231 ms
Max solver time: 60.2813 ms, running benchmark .7..1....5....8.7..9.4...2...3..9...6.....2...8..3...4...1.9..3.5....6....8..4...
----------------------
Benchmarking fastsolveBM()
................................................................................................................................................................
Average solver time: 0.3954333333333333 ms
Max solver time: 11.9924 ms, running benchmark ..2.....18..6..9..5....4..7.9.....3.3.....1.8.....2.4..7....6..9..3..17.....8....
----------------------
Benchmarking solveDLX()
................................................................................................................................................................
Average solver time: 0.4467437179487179 ms
Max solver time: 7.102 ms, running benchmark .........6..4..1...47..2.6.8.6........93..8.2....84..7...7...863...267..........1
```
# Screenshot
Sudoku game:

![Screenshot](images/sudoku.png)
