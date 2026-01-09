package game;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import java.awt.*;
import java.awt.event.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import solver.Sudoku;
/**
 * Interactive Sudoku Game using java Swing for the UI
 *
 * usage: java game.SudokuGame
 */
public class SudokuGame extends JFrame {
    private static final int N_FILLED_CELLS_EASY = 50;
    private static final int N_FILLED_CELLS_MEDIUM = 40;
    private static final int N_FILLED_CELLS_HARD = 30;

	private static final int GRID_SIZE = 9;
    private static final int SUBGRID_SIZE = 3;
    
    private JTextField[][] cells = new JTextField[GRID_SIZE][GRID_SIZE];
    private int[][] initialBoard;
    private boolean isUpdating = false;

	private Sudoku sudoku;

    public SudokuGame() {
        setTitle("Sudoku Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel boardPanel = new JPanel(new GridLayout(GRID_SIZE, GRID_SIZE));
        boardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        initializeGrid(boardPanel);
        
		sudoku = new Sudoku();
		generateNewSudoku(N_FILLED_CELLS_MEDIUM);
        
        JPanel controlPanel = new JPanel();
		JButton newGameEasyButton = new JButton("New Easy");
		JButton newGameMediumButton = new JButton("New Medium");
		JButton newGameHardButton = new JButton("New Hard");
        JButton resetButton = new JButton("Reset");
        JButton solveButton = new JButton("Solve");

		newGameEasyButton.addActionListener(e -> generateNewSudoku(N_FILLED_CELLS_EASY));
		newGameMediumButton.addActionListener(e -> generateNewSudoku(N_FILLED_CELLS_MEDIUM));
		newGameHardButton.addActionListener(e -> generateNewSudoku(N_FILLED_CELLS_HARD));
        resetButton.addActionListener(e -> resetBoard());
        solveButton.addActionListener(e -> solveBoard());
        
		controlPanel.add(newGameEasyButton);
		controlPanel.add(newGameMediumButton);
		controlPanel.add(newGameHardButton);
        controlPanel.add(resetButton);
        controlPanel.add(solveButton);

        add(boardPanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initializeGrid(JPanel boardPanel) {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                JTextField cell = new JTextField();
                cell.setHorizontalAlignment(JTextField.CENTER);
                cell.setFont(new Font("SansSerif", Font.BOLD, 20));
                cell.setPreferredSize(new Dimension(50, 50));
                
                // Add a border for 3x3 blocks
                int top = (row % 3 == 0) ? 2 : 1;
                int left = (col % 3 == 0) ? 2 : 1;
                int bottom = (row == 8) ? 2 : 0;
                int right = (col == 8) ? 2 : 0;
                cell.setBorder(BorderFactory.createMatteBorder(top, left, bottom, right, Color.BLACK));

                // Restrict input to numbers 1-9 and single character
                ((AbstractDocument) cell.getDocument()).setDocumentFilter(new CheckSingleDigit1to9());
                
                // Add user input listener with validation
                final int r = row;
                final int c = col;
                cell.getDocument().addDocumentListener(new DocumentListener() {
                    public void insertUpdate(DocumentEvent e) { validate(); }
                    public void removeUpdate(DocumentEvent e) { validate(); }
                    public void changedUpdate(DocumentEvent e) { validate(); }

                    private void validate() {
                        if (isUpdating) return;
                        SwingUtilities.invokeLater(() -> checkMove(r, c));
                    }
                });
				cell.addKeyListener(new KeyAdapter() {
					public void keyPressed(KeyEvent e) {
						switch (e.getKeyCode()) {
							case KeyEvent.VK_UP:
								if (r > 0) cells[r - 1][c].requestFocus();
								break;
							case KeyEvent.VK_DOWN:
								if (r < GRID_SIZE - 1) cells[r + 1][c].requestFocus();
								break;
							case KeyEvent.VK_LEFT:
								if (c > 0) cells[r][c - 1].requestFocus();
								break;
							case KeyEvent.VK_RIGHT:
								if (c < GRID_SIZE - 1) cells[r][c + 1].requestFocus();
								break;
						}
					}
				});
				cell.addFocusListener(new FocusAdapter() {
					public void focusGained(FocusEvent e) {
						cell.selectAll();
					}
				});

                cells[row][col] = cell;
                boardPanel.add(cell);
            }
        }
    }

    private void generateNewSudoku(int nvalues) {
        isUpdating = true;
		sudoku.generate(nvalues);
		initialBoard = sudoku.cloneBoard();

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (initialBoard[r][c] != 0) {
					cells[r][c].setForeground(Color.BLACK);
                    cells[r][c].setText(String.valueOf(initialBoard[r][c]));
                    cells[r][c].setEditable(false);
                    cells[r][c].setBackground(new Color(230, 230, 230));
                } else {
                    cells[r][c].setText("");
                    cells[r][c].setEditable(true);
                    cells[r][c].setBackground(Color.WHITE);
                }
            }
        }
        isUpdating = false;
    }

    private void checkMove(int r, int c) {
        String text = cells[r][c].getText();
        if (text.isEmpty()) {
            cells[r][c].setForeground(Color.BLACK);
            return;
        }

        int val = Integer.parseInt(text);
        boolean isValid = true;

        // Check row and column
        for (int i = 0; i < GRID_SIZE; i++) {
            if (i != c && cells[r][i].getText().equals(text)) isValid = false;
            if (i != r && cells[i][c].getText().equals(text)) isValid = false;
        }

        // Check 3x3 subgrid
        int boxRow = r - r % 3;
        int boxCol = c - c % 3;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                if ((boxRow + i != r || boxCol + j != c) && 
                    cells[boxRow + i][boxCol + j].getText().equals(text)) {
                    isValid = false;
                }

        if (isValid) {
            cells[r][c].setForeground(Color.BLUE); // User input in Blue
            checkGameCompletion();
        } else {
            cells[r][c].setForeground(Color.RED); //Wrong digit in Red
        }
    }

    private void checkGameCompletion() {
        for (int r = 0; r < GRID_SIZE; r++)
            for (int c = 0; c < GRID_SIZE; c++) {
                String val = cells[r][c].getText();
                if (val.isEmpty() || cells[r][c].getForeground().equals(Color.RED)) return;
            }
        JOptionPane.showMessageDialog(this, "Sudoku Solved!", "Congratulations", JOptionPane.INFORMATION_MESSAGE);
    }

    private void resetBoard() {
        isUpdating = true;
        for (int r = 0; r < GRID_SIZE; r++)
            for (int c = 0; c < GRID_SIZE; c++)
                if (initialBoard[r][c] == 0) {
                    cells[r][c].setText("");
                    cells[r][c].setForeground(Color.BLACK);
                }
        isUpdating = false;
    }

    private void solveBoard() {
        if (sudoku.fastsolveBM()) {
            isUpdating = true;
			int[][] solvedBoard = sudoku.cloneBoard();
            for (int r = 0; r < GRID_SIZE; r++) {
                for (int c = 0; c < GRID_SIZE; c++) {
                    cells[r][c].setText(String.valueOf(solvedBoard[r][c]));
                    if (initialBoard[r][c] == 0) {
                        cells[r][c].setForeground(Color.BLUE);
                    }
                }
            }
            isUpdating = false;
            JOptionPane.showMessageDialog(this, "Sudoku Solved", "Solver", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    class CheckSingleDigit1to9 extends DocumentFilter {
        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text.isEmpty())
                super.replace(fb, offset, length, text, attrs);
            else if (text.length() == 1 && text.matches("[1-9]") && fb.getDocument().getLength() - length + text.length() <= 1)
                super.replace(fb, offset, length, text, attrs);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SudokuGame());
    }
}