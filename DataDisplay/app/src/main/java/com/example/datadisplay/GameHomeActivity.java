package com.example.datadisplay;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class GameHomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_home);

        MaterialCardView snakeCard = findViewById(R.id.cardSnake);
        snakeCard.setOnClickListener(v -> {
            Intent intent = new Intent(GameHomeActivity.this, SnakeGameActivity.class);
            startActivity(intent);
        });

        MaterialCardView ticTacToeCard = findViewById(R.id.cardTicTacToe);
        ticTacToeCard.setOnClickListener(v -> {
            Intent intent = new Intent(GameHomeActivity.this, TicTacToeActivity.class);
            startActivity(intent);
        });
    }
}