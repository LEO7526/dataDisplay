package com.example.datadisplay;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SnakeGameActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "snake_game_prefs";
    private static final String KEY_HIGH_SCORE = "snake_high_score";

    private LinearLayout menuContainer;
    private LinearLayout gameplayContainer;
    private TextView highScoreText;
    private TextView scoreText;
    private SnakeGameView snakeGameView;

    private ToneGenerator toneGenerator;
    private final Handler musicHandler = new Handler(Looper.getMainLooper());
    private boolean musicPlaying = false;
    private int musicStep = 0;

    private final Runnable backgroundMusicLoop = new Runnable() {
        @Override
        public void run() {
            if (!musicPlaying || toneGenerator == null) {
                return;
            }

            int toneType;
            switch (musicStep % 4) {
                case 0:
                    toneType = ToneGenerator.TONE_DTMF_1;
                    break;
                case 1:
                    toneType = ToneGenerator.TONE_DTMF_3;
                    break;
                case 2:
                    toneType = ToneGenerator.TONE_DTMF_5;
                    break;
                default:
                    toneType = ToneGenerator.TONE_DTMF_8;
                    break;
            }

            toneGenerator.startTone(toneType, 110);
            musicStep++;
            musicHandler.postDelayed(this, 230);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_snake_game);

        menuContainer = findViewById(R.id.menuContainer);
        gameplayContainer = findViewById(R.id.gameplayContainer);
        highScoreText = findViewById(R.id.txtSnakeHighScore);
        scoreText = findViewById(R.id.txtSnakeScore);
        snakeGameView = findViewById(R.id.snakeGameView);

        Button startButton = findViewById(R.id.btnSnakeStart);
        Button menuButton = findViewById(R.id.btnSnakeMainMenu);
        Button upButton = findViewById(R.id.btnSnakeUp);
        Button downButton = findViewById(R.id.btnSnakeDown);
        Button leftButton = findViewById(R.id.btnSnakeLeft);
        Button rightButton = findViewById(R.id.btnSnakeRight);

        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 45);

        snakeGameView.setGameEventListener(new SnakeGameView.GameEventListener() {
            @Override
            public void onScoreChanged(int score) {
                scoreText.setText(getString(R.string.snake_score_text, score));
            }

            @Override
            public void onFoodEaten() {
                playEatSound();
            }

            @Override
            public void onGameOver(int finalScore) {
                handleGameOver(finalScore);
            }
        });

        startButton.setOnClickListener(v -> startSnakeGame());
        menuButton.setOnClickListener(v -> showMainMenu());

        upButton.setOnClickListener(v -> snakeGameView.changeDirection(SnakeGameView.Direction.UP));
        downButton.setOnClickListener(v -> snakeGameView.changeDirection(SnakeGameView.Direction.DOWN));
        leftButton.setOnClickListener(v -> snakeGameView.changeDirection(SnakeGameView.Direction.LEFT));
        rightButton.setOnClickListener(v -> snakeGameView.changeDirection(SnakeGameView.Direction.RIGHT));

        showMainMenu();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBackgroundMusic();
        snakeGameView.stopGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBackgroundMusic();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void startSnakeGame() {
        menuContainer.setVisibility(View.GONE);
        gameplayContainer.setVisibility(View.VISIBLE);
        scoreText.setText(getString(R.string.snake_score_text, 0));
        snakeGameView.startNewGame();
        startBackgroundMusic();
    }

    private void showMainMenu() {
        stopBackgroundMusic();
        snakeGameView.stopGame();
        gameplayContainer.setVisibility(View.GONE);
        menuContainer.setVisibility(View.VISIBLE);
        highScoreText.setText(getString(R.string.snake_high_score_text, getHighScore()));
    }

    private void handleGameOver(int finalScore) {
        stopBackgroundMusic();
        playGameOverSound();

        boolean newRecord = saveHighScoreIfNeeded(finalScore);
        if (newRecord) {
            Toast.makeText(this, getString(R.string.snake_new_high_score, finalScore), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.snake_game_over, finalScore), Toast.LENGTH_SHORT).show();
        }

        showMainMenu();
    }

    private int getHighScore() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_HIGH_SCORE, 0);
    }

    private boolean saveHighScoreIfNeeded(int score) {
        int bestScore = getHighScore();
        if (score > bestScore) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_HIGH_SCORE, score)
                    .apply();
            return true;
        }
        return false;
    }

    private void startBackgroundMusic() {
        if (musicPlaying) {
            return;
        }
        musicPlaying = true;
        musicStep = 0;
        musicHandler.post(backgroundMusicLoop);
    }

    private void stopBackgroundMusic() {
        musicPlaying = false;
        musicHandler.removeCallbacks(backgroundMusicLoop);
    }

    private void playEatSound() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 90);
        }
    }

    private void playGameOverSound() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_SUP_ERROR, 280);
        }
    }
}