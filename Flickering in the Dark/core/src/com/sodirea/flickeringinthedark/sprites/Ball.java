package com.sodirea.flickeringinthedark.sprites;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.sodirea.flickeringinthedark.FlickeringInTheDark;

public class Ball {

    public static final int GRAVITY = -8;
    public static final float SCALING_FACTOR = 0.17f;
    private Texture ground;
    private Texture wall;
    private Texture ball;
    private Vector2 position;
    private Vector2 velocity;
    private Rectangle bounds;
    private float minPos;

    public Ball(float x, float y) {
        ground = new Texture("ground.png");
        wall = new Texture("wall.png");
        ball = new Texture("ball.png");
        position = new Vector2(x, y);
        velocity = new Vector2(0, 0);
        bounds = new Rectangle(position.x, position.y, ball.getWidth(), ball.getHeight());
        minPos = ground.getHeight();
    }

    public Vector2 getPosition() {
        return position;
    }

    public Texture getTexture(){
        return ball;
    }

    public boolean boundsOverlapped(Rectangle platformBounds) {
        return bounds.overlaps(platformBounds);
    }

    public void setNewMinPosition(float newMinPos) {
        minPos = newMinPos;
    }

    public void resetVelocityY() {
        velocity.y = 0;
    }

    public void setPosition(float x, float y) {
        position.set(x, y);
    }

    public void jump() {
        if (position.y <= minPos) {
            velocity.y = 140;
        }
    }

    public void moveLeft(float x) {
        velocity.x = x;
    }

    public void moveRight(float y) {
        velocity.x = y;
    }

    public void update(float dt) {
        position.add(SCALING_FACTOR * velocity.x, SCALING_FACTOR * velocity.y);
        if (position.y > minPos) { // add gravity to velocity if off the ground
            velocity.y += GRAVITY;
        } else {
            position.y = minPos;
            velocity.y = 0;
        }
        if (position.x <= wall.getWidth()) {
            position.x = wall.getWidth();
        }
        if (position.x + ball.getWidth() >= FlickeringInTheDark.WIDTH - wall.getWidth()) {
            position.x = FlickeringInTheDark.WIDTH - wall.getWidth() - ball.getWidth();
        }
        bounds.setPosition(position.x, position.y);
    }

    public void render(SpriteBatch sb) {
        sb.draw(ball, position.x, position.y);
    }

    public void dispose() {
        ground.dispose();
        wall.dispose();
        ball.dispose();
    }
}
