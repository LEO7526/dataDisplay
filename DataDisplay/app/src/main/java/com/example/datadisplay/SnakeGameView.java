package com.example.datadisplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SnakeGameView extends View {

    public interface GameEventListener {
        void onScoreChanged(int score);

        void onFoodEaten();

        void onGameOver(int finalScore);
    }

    public enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    private static final int GRID_COLUMNS = 20;
    private static final int INITIAL_LENGTH = 3;
    private static final int TICK_MS = 180;
    private static final float SWIPE_THRESHOLD = 60f;

    private final List<Point> snakeSegments = new ArrayList<>();
    private final Random random = new Random();
    private final Handler gameHandler = new Handler(Looper.getMainLooper());

    private final Paint boardPaint = new Paint();
    private final Paint snakePaint = new Paint();
    private final Paint snakeHeadPaint = new Paint();
    private final Paint foodPaint = new Paint();

    private final Runnable gameLoopRunnable = new Runnable() {
        @Override
        public void run() {
            if (!gameRunning) {
                return;
            }

            stepGame();
            invalidate();

            if (gameRunning) {
                gameHandler.postDelayed(this, TICK_MS);
            }
        }
    };

    private GameEventListener gameEventListener;

    private Point foodPoint = new Point();
    private Direction currentDirection = Direction.RIGHT;
    private Direction pendingDirection = Direction.RIGHT;

    private int score = 0;
    private int cellSize = 1;
    private int gridRows = 18;
    private int boardOffsetX = 0;
    private int boardOffsetY = 0;

    private boolean gameRunning = false;

    private float touchStartX;
    private float touchStartY;

    public SnakeGameView(Context context) {
        super(context);
        initPaints();
    }

    public SnakeGameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    public SnakeGameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    private void initPaints() {
        boardPaint.setColor(Color.parseColor("#101820"));
        boardPaint.setStyle(Paint.Style.FILL);

        snakePaint.setColor(Color.parseColor("#66BB6A"));
        snakePaint.setStyle(Paint.Style.FILL);

        snakeHeadPaint.setColor(Color.parseColor("#2E7D32"));
        snakeHeadPaint.setStyle(Paint.Style.FILL);

        foodPaint.setColor(Color.parseColor("#EF5350"));
        foodPaint.setStyle(Paint.Style.FILL);
    }

    public void setGameEventListener(GameEventListener gameEventListener) {
        this.gameEventListener = gameEventListener;
    }

    public void startNewGame() {
        if (gridRows <= 0) {
            post(this::startNewGame);
            return;
        }

        stopGame();
        resetState();
        gameRunning = true;
        gameHandler.postDelayed(gameLoopRunnable, TICK_MS);
        invalidate();
    }

    public void stopGame() {
        gameRunning = false;
        gameHandler.removeCallbacks(gameLoopRunnable);
    }

    public void changeDirection(Direction direction) {
        if (!isOpposite(currentDirection, direction)) {
            pendingDirection = direction;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }

        cellSize = Math.max(1, w / GRID_COLUMNS);
        gridRows = Math.max(14, h / cellSize);

        int boardWidth = GRID_COLUMNS * cellSize;
        int boardHeight = gridRows * cellSize;

        boardOffsetX = (w - boardWidth) / 2;
        boardOffsetY = (h - boardHeight) / 2;

        if (snakeSegments.isEmpty()) {
            resetState();
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int boardWidth = GRID_COLUMNS * cellSize;
        int boardHeight = gridRows * cellSize;

        canvas.drawRect(
                boardOffsetX,
                boardOffsetY,
                boardOffsetX + boardWidth,
                boardOffsetY + boardHeight,
                boardPaint
        );

        float foodLeft = boardOffsetX + (foodPoint.x * cellSize);
        float foodTop = boardOffsetY + (foodPoint.y * cellSize);
        canvas.drawRect(
                foodLeft,
                foodTop,
                foodLeft + cellSize,
                foodTop + cellSize,
                foodPaint
        );

        for (int index = 0; index < snakeSegments.size(); index++) {
            Point segment = snakeSegments.get(index);
            float left = boardOffsetX + (segment.x * cellSize);
            float top = boardOffsetY + (segment.y * cellSize);

            Paint paint = index == 0 ? snakeHeadPaint : snakePaint;
            canvas.drawRect(
                    left + 2,
                    top + 2,
                    left + cellSize - 2,
                    top + cellSize - 2,
                    paint
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                float deltaX = event.getX() - touchStartX;
                float deltaY = event.getY() - touchStartY;

                if (Math.abs(deltaX) < SWIPE_THRESHOLD && Math.abs(deltaY) < SWIPE_THRESHOLD) {
                    return true;
                }

                if (Math.abs(deltaX) > Math.abs(deltaY)) {
                    changeDirection(deltaX > 0 ? Direction.RIGHT : Direction.LEFT);
                } else {
                    changeDirection(deltaY > 0 ? Direction.DOWN : Direction.UP);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void resetState() {
        snakeSegments.clear();

        int centerX = GRID_COLUMNS / 2;
        int centerY = gridRows / 2;

        for (int offset = 0; offset < INITIAL_LENGTH; offset++) {
            snakeSegments.add(new Point(centerX - offset, centerY));
        }

        currentDirection = Direction.RIGHT;
        pendingDirection = Direction.RIGHT;
        score = 0;

        spawnFood();

        if (gameEventListener != null) {
            gameEventListener.onScoreChanged(score);
        }
    }

    private void stepGame() {
        currentDirection = pendingDirection;

        Point head = snakeSegments.get(0);
        Point newHead = new Point(
                head.x + getDeltaX(currentDirection),
                head.y + getDeltaY(currentDirection)
        );

        if (hitsWall(newHead) || hitsSelf(newHead)) {
            stopGame();
            if (gameEventListener != null) {
                gameEventListener.onGameOver(score);
            }
            return;
        }

        snakeSegments.add(0, newHead);

        if (newHead.equals(foodPoint)) {
            score += 10;
            if (gameEventListener != null) {
                gameEventListener.onScoreChanged(score);
                gameEventListener.onFoodEaten();
            }
            spawnFood();
        } else {
            snakeSegments.remove(snakeSegments.size() - 1);
        }
    }

    private void spawnFood() {
        int attempts = 0;
        do {
            foodPoint = new Point(random.nextInt(GRID_COLUMNS), random.nextInt(gridRows));
            attempts++;
        } while (containsPoint(foodPoint) && attempts < 500);
    }

    private boolean hitsWall(Point point) {
        return point.x < 0 || point.x >= GRID_COLUMNS || point.y < 0 || point.y >= gridRows;
    }

    private boolean hitsSelf(Point point) {
        return containsPoint(point);
    }

    private boolean containsPoint(Point point) {
        for (Point segment : snakeSegments) {
            if (segment.equals(point)) {
                return true;
            }
        }
        return false;
    }

    private int getDeltaX(Direction direction) {
        switch (direction) {
            case LEFT:
                return -1;
            case RIGHT:
                return 1;
            default:
                return 0;
        }
    }

    private int getDeltaY(Direction direction) {
        switch (direction) {
            case UP:
                return -1;
            case DOWN:
                return 1;
            default:
                return 0;
        }
    }

    private boolean isOpposite(Direction first, Direction second) {
        return (first == Direction.UP && second == Direction.DOWN)
                || (first == Direction.DOWN && second == Direction.UP)
                || (first == Direction.LEFT && second == Direction.RIGHT)
                || (first == Direction.RIGHT && second == Direction.LEFT);
    }
}