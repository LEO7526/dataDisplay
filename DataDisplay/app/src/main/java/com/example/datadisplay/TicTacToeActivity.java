package com.example.datadisplay;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TicTacToeActivity extends AppCompatActivity {

    private final Button[] cells = new Button[9];
    private char currentPlayer = 'X';
    private boolean gameActive = true;
    private int moveCount = 0;

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tic_tac_toe);

        statusText = findViewById(R.id.txtGameStatus);
        Button resetButton = findViewById(R.id.btnRestartGame);

        int[] cellIds = new int[]{
                R.id.cell0, R.id.cell1, R.id.cell2,
                R.id.cell3, R.id.cell4, R.id.cell5,
                R.id.cell6, R.id.cell7, R.id.cell8
        };

        for (int index = 0; index < cellIds.length; index++) {
            cells[index] = findViewById(cellIds[index]);
            int cellIndex = index;
            cells[index].setOnClickListener(v -> handleMove(cellIndex));
        }

        resetButton.setOnClickListener(v -> resetGame());
        updateTurnStatus();
    }

    private void handleMove(int cellIndex) {
        if (!gameActive) {
            return;
        }

        Button cellButton = cells[cellIndex];
        if (!cellButton.getText().toString().isEmpty()) {
            return;
        }

        String currentMark = String.valueOf(currentPlayer);
        cellButton.setText(currentMark);
        moveCount++;

        if (isWinningPlayer(currentMark)) {
            statusText.setText(getString(R.string.tictactoe_win_text, currentMark));
            gameActive = false;
            return;
        }

        if (moveCount == 9) {
            statusText.setText(R.string.tictactoe_draw_text);
            gameActive = false;
            return;
        }

        currentPlayer = currentPlayer == 'X' ? 'O' : 'X';
        updateTurnStatus();
    }

    private boolean isWinningPlayer(String mark) {
        int[][] winLines = new int[][]{
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
                {0, 4, 8}, {2, 4, 6}
        };

        for (int[] line : winLines) {
            String first = cells[line[0]].getText().toString();
            String second = cells[line[1]].getText().toString();
            String third = cells[line[2]].getText().toString();
            if (mark.equals(first) && mark.equals(second) && mark.equals(third)) {
                return true;
            }
        }
        return false;
    }

    private void updateTurnStatus() {
        statusText.setText(getString(R.string.tictactoe_turn_text, String.valueOf(currentPlayer)));
    }

    private void resetGame() {
        for (Button cell : cells) {
            cell.setText("");
        }
        currentPlayer = 'X';
        moveCount = 0;
        gameActive = true;
        updateTurnStatus();
    }
}