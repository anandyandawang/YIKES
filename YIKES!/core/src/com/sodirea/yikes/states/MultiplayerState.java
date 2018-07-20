package com.sodirea.yikes.states;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.sodirea.yikes.Yikes;
import com.sodirea.yikes.sprites.Ball;
import com.sodirea.yikes.sprites.Platform;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import static com.sodirea.yikes.states.PlayState.GRAVITY;
import static com.sodirea.yikes.states.PlayState.NUM_PLATFORMS;
import static com.sodirea.yikes.states.PlayState.PIXELS_TO_METERS;
import static com.sodirea.yikes.states.PlayState.PLATFORM_INTERVALS;
import static com.sodirea.yikes.states.PlayState.TIME_STEP;

public class MultiplayerState extends State {

    private final float UPDATE_TIME = 1/60f;
    float timer;
    private Socket socket;
    private Texture bg;
    private Texture ground;
    private Texture wall;
    private Texture ballTexture;
    private float totalTimePassed;
    private boolean startCamera;
    private World world;
    private Array<Platform> platformArray;
    private Array<Vector2> platformPositionArray;
    private Array<Integer> platformWidthArray;
    private boolean needsPlatforms;
    private int toRepositionIndex;
    private boolean giveServerPositionCoordinates;
    private BodyDef groundBodyDef;
    private Body groundBody;
    private PolygonShape groundBox;
    private BodyDef wallBodyDef;
    private Body wallBody;
    private PolygonShape wallBox;
    private FixtureDef wallFixtureDef;
    private Fixture wallFixture;
    private BodyDef wallBodyDef2;
    private Body wallBody2;
    private PolygonShape wallBox2;
    private FixtureDef wallFixtureDef2;
    private Fixture wallFixture2;
    private Ball player;
    private boolean playerConnected;
    private HashMap<String, Ball> otherPlayers;
    private HashMap<String, Vector2> otherPlayersPosition;

    public MultiplayerState(GameStateManager gsm) {
        super(gsm);
        bg = new Texture("bg.png");
        ground = new Texture("ground.png");
        wall = new Texture("wall.png");
        ballTexture = new Texture("ball.png");
        totalTimePassed = 0;
        startCamera = false;
        platformArray = new Array<Platform>();
        platformPositionArray = new Array<Vector2>();
        platformWidthArray = new Array<Integer>();
        needsPlatforms = true;
        toRepositionIndex = -1; // if it is not -1, then a platform needs to be repositioned
        giveServerPositionCoordinates = false;
        cam.setToOrtho(false, Yikes.WIDTH, Yikes.HEIGHT);

        Box2D.init();
        world = new World(new Vector2(0, GRAVITY), true);
        world.setContactListener(new ContactListener() {
            @Override
            public void beginContact(Contact contact) {
                if (contact.getFixtureA().getBody() != null && contact.getFixtureB().getBody().getUserData() != null) {
                    if (contact.getFixtureA().getBody().getUserData() == player || contact.getFixtureB().getBody().getUserData() == player) { // if the foot sensor fixture of the ball touches a platform, and the lowest point of the ball is higher than the highest point of the touched platform, then clear the platform
                        player.addNumberOfFootContacts(); // add to the total number of contact points
                        Platform platform = null;
                        if (contact.getFixtureA().getBody().getUserData() instanceof Platform) {
                            platform = (Platform) contact.getFixtureA().getBody().getUserData();
                        } else if (contact.getFixtureB().getBody().getUserData() instanceof Platform) {
                            platform = (Platform) contact.getFixtureB().getBody().getUserData();
                        }
                        if (platform != null && player.getPosition().y > platform.getPosition().y + platform.getTexture().getHeight() && !platform.getIsCleared()) {
                            if (!startCamera) {
                                startCamera = true;
                                socket.emit("startCamera"); // make camera start for every player
                                giveServerPositionCoordinates = true; // the first player to step on a platform will give the server coordinates to determine when to reposition platforms
                            }
                        }
                    }
                }
            }

            @Override
            public void endContact(Contact contact) {
                if (contact.getFixtureA().getBody() != null && contact.getFixtureB().getBody().getUserData() != null) {
                    if (contact.getFixtureA().getBody().getUserData() == player || contact.getFixtureB().getBody().getUserData() == player) {
                        player.lessNumberOfFootContacts();
                    }
                }
            }

            @Override
            public void preSolve(Contact contact, Manifold oldManifold) {
            }

            @Override
            public void postSolve(Contact contact, ContactImpulse impulse) {
            }
        });

        groundBodyDef = new BodyDef();
        groundBodyDef.position.set(ground.getWidth() / 2 * PIXELS_TO_METERS, ground.getHeight() / 2 * PIXELS_TO_METERS);
        groundBody = world.createBody(groundBodyDef);
        groundBox = new PolygonShape();
        groundBox.setAsBox(ground.getWidth() / 2 * PIXELS_TO_METERS, ground.getHeight() / 2 * PIXELS_TO_METERS);
        groundBody.createFixture(groundBox, 0.0f);

        wallBodyDef = new BodyDef();
        wallBodyDef.position.set(wall.getWidth() / 2 * PIXELS_TO_METERS, wall.getHeight() / 2 * PIXELS_TO_METERS);
        wallBody = world.createBody(wallBodyDef);
        wallBox = new PolygonShape();
        wallBox.setAsBox(wall.getWidth() / 2 * PIXELS_TO_METERS, wall.getHeight() / 2 * PIXELS_TO_METERS);
        wallFixtureDef = new FixtureDef();
        wallFixtureDef.shape = wallBox;
        wallFixtureDef.density = 0.0f;
        wallFixtureDef.friction = 0.0f;
        wallFixture = wallBody.createFixture(wallFixtureDef);

        wallBodyDef2 = new BodyDef();
        wallBodyDef2.position.set((cam.position.x + cam.viewportWidth / 2 - wall.getWidth() + wall.getWidth() / 2) * PIXELS_TO_METERS, wall.getHeight() / 2 * PIXELS_TO_METERS);
        wallBody2 = world.createBody(wallBodyDef2);
        wallBox2 = new PolygonShape();
        wallBox2.setAsBox(wall.getWidth() / 2 * PIXELS_TO_METERS, wall.getHeight() / 2 * PIXELS_TO_METERS);
        wallFixtureDef2 = new FixtureDef();
        wallFixtureDef2.shape = wallBox2;
        wallFixtureDef2.density = 0.0f;
        wallFixtureDef2.friction = 0.0f;
        wallFixture2 = wallBody2.createFixture(wallFixtureDef2);

        playerConnected = false;
        otherPlayers = new HashMap<String, Ball>();
        otherPlayersPosition = new HashMap<String, Vector2>();

        connectSocket();
        configSocketEvents();
    }

    @Override
    protected void handleInput() {
        if (Gdx.input.justTouched() && player != null && player.getNumberOfFootContacts() > 0) {
            player.setBodyLinearVelocity(player.getBodyLinearVelocity().x, 50f);
        }
        float accelX = Gdx.input.getAccelerometerX();
        if (accelX > 0 && player != null) {
            player.setBodyLinearVelocity(-20f * accelX, player.getBodyLinearVelocity().y);
        }
        if (accelX < 0 && player != null) {
            player.setBodyLinearVelocity(-20f * accelX, player.getBodyLinearVelocity().y);
        }
    }

    @Override
    public void update(float dt) {
        timer += dt;
        if (timer >= UPDATE_TIME && player != null) {
            timer = 0;
            JSONObject data = new JSONObject();
            try {
                data.put("x", player.getPosition().x);
                data.put("y", player.getPosition().y);
                socket.emit("playerUpdate", data);
            } catch(JSONException e) {
                Gdx.app.log("SOCKET.IO", "Error sending update data");
            }
            if (needsPlatforms) {
                for (int i = 0; i < platformPositionArray.size; i++) { // constantly updates platformArray with new coordinates from position and width arrays (if they are updated/repositioned by the server)
                    Vector2 position = platformPositionArray.get(i);
                    Integer width = platformWidthArray.get(i);
                    platformArray.add(new Platform(position.x, position.y, width, world));
                }
                needsPlatforms = false;
            }
        }
        if (toRepositionIndex != -1) {
            platformArray.get(toRepositionIndex).reposition(platformPositionArray.get(toRepositionIndex).x, platformPositionArray.get(toRepositionIndex).y, platformWidthArray.get(toRepositionIndex));
            toRepositionIndex = -1;
        }
        handleInput();
        if (playerConnected && player == null) {
            player = new Ball(cam.position.x - ballTexture.getWidth() / 2, ground.getHeight(), world);
        }

        for (int i = 0; i < platformArray.size; i++) {
            Platform platform = platformArray.get(i);
            platform.update(dt);
            if (giveServerPositionCoordinates) { // if our representative needs to reposition a platform, all other players also reposition
                if (platform.getPosition().y + platform.getTexture().getHeight() < cam.position.y - cam.viewportHeight / 2) {
                    platform.reposition(platform.getPosition().y + PLATFORM_INTERVALS * NUM_PLATFORMS);
                    JSONObject repositionedPlatform = new JSONObject();
                    try {
                        repositionedPlatform.put("index", i);
                        repositionedPlatform.put("x", platform.getPosition().x);
                        repositionedPlatform.put("y", platform.getPosition().y);
                        repositionedPlatform.put("width", platform.getHoleWidth());
                        socket.emit("repositionPlatform", repositionedPlatform);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        for (HashMap.Entry<String, Vector2> entry : otherPlayersPosition.entrySet()) { // updating the hashmap with list of ball players with the positions hashmap
            String id = entry.getKey();
            Vector2 position = otherPlayersPosition.get(id);
            if (!otherPlayers.containsKey(entry.getKey())) { // found an id in position array that isn't in the original array
                otherPlayers.put(id, new Ball(position.x, position.y, world));
            } else { // if it already contains the id, then just update its ball's position
                Ball otherPlayer = otherPlayers.get(id);
                otherPlayer.setPosition(position.x, position.y);
            }
        }

        if (player != null) {
            player.update(dt);
        }
        for (HashMap.Entry<String, Ball> entry : otherPlayers.entrySet()) {
            entry.getValue().update(dt);
        }

        if (startCamera) {
            if (totalTimePassed < 60) {
                totalTimePassed += dt;
            }
            cam.position.y += 4 * Ball.SCALING_FACTOR * (Math.pow(1.02, totalTimePassed) + 2);
            cam.update();
        }

        wallBody.setTransform(new Vector2(wallBody.getPosition().x, cam.position.y * PIXELS_TO_METERS), wallBody.getAngle());
        wallBody2.setTransform(new Vector2(wallBody2.getPosition().x, cam.position.y * PIXELS_TO_METERS), wallBody2.getAngle());

        world.step(TIME_STEP, 6, 2);
    }

    @Override
    public void render(SpriteBatch sb) {
        sb.setProjectionMatrix(cam.combined);
        sb.begin();
        sb.draw(bg, 0, cam.position.y - cam.viewportHeight / 2);
        sb.draw(ground, 0, 0);
        sb.draw(wall, 0, cam.position.y - cam.viewportHeight / 2);
        sb.draw(wall, cam.position.x + cam.viewportWidth / 2 - wall.getWidth(), cam.position.y - cam.viewportHeight / 2);
        if (player != null) {
            player.render(sb);
        }
        for (HashMap.Entry<String, Ball> entry : otherPlayers.entrySet()) {
            entry.getValue().render(sb);
        }
        for (Platform platform : platformArray) {
            platform.render(sb);
        }
        sb.end();
    }

    @Override
    public void dispose() {
        bg.dispose();
        ground.dispose();
        wall.dispose();
        player.dispose();
        ballTexture.dispose();
        for (HashMap.Entry<String, Ball> entry : otherPlayers.entrySet()) {
            entry.getValue().dispose();
        }
    }

    public void connectSocket() {
        try {
            socket = IO.socket("http://192.168.0.13:8080");
            socket.connect();
        } catch(Exception e) {
            System.out.println(e);
        }
    }

    public void configSocketEvents() {
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Gdx.app.log("SocketIO", "Connected");
                playerConnected = true;
            }
        }).on("socketID", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String id = data.getString("id");
                    Gdx.app.log("SocketIO", "My ID: " + id);
                } catch (JSONException e) {
                    Gdx.app.log("SocketIO", "Error getting ID");
                }
            }
        }).on("newPlayer", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String id = data.getString("id");
                    Gdx.app.log("SocketIO", "New Player Connected: " + id);
                    otherPlayersPosition.put(id, new Vector2(200, 200));
                } catch (JSONException e) {
                    Gdx.app.log("SocketIO", "Error getting new Player ID");
                }
            }
        }).on("playerDisconnected", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String id = data.getString("id");
                    otherPlayers.remove(id);
                    otherPlayersPosition.remove(id);
                } catch (JSONException e) {
                    Gdx.app.log("SocketIO", "Error getting disconnected Player ID");
                }
            }
        }).on("getPlayers", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONArray objects = (JSONArray) args[0];
                try {
                    for (int i = 0; i < objects.length(); i++) {
                        Vector2 position = new Vector2();
                        position.x = ((Double) objects.getJSONObject(i).getDouble("x")).floatValue();
                        position.y = ((Double) objects.getJSONObject(i).getDouble("y")).floatValue();
                        otherPlayersPosition.put(objects.getJSONObject(i).getString("id"), position);
                    }
                } catch(JSONException e) {

                }
            }
        }).on("playerUpdate", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String id = data.getString("id");
                    Double x = data.getDouble("x");
                    Double y = data.getDouble("y");
                    if (otherPlayersPosition.get(id) != null) {
                        otherPlayersPosition.put(id, new Vector2(x.floatValue(), y.floatValue()));
                    }
                } catch (JSONException e) {
                    Gdx.app.log("SocketIO", "Error getting disconnected Player ID");
                }
            }
        }).on("getPlatforms", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONArray objects = (JSONArray) args[0];
                try {
                    for (int i = 0; i < objects.length(); i++) {
                        Vector2 position = new Vector2();
                        position.x = ((Double) objects.getJSONObject(i).getDouble("x")).floatValue();
                        position.y = ((Double) objects.getJSONObject(i).getDouble("y")).floatValue();
                        Integer width = new Integer(objects.getJSONObject(i).getInt("width"));
                        platformWidthArray.add(width);
                        platformPositionArray.add(position);
                    }
                } catch (JSONException e) {

                }
            }
        }).on("startCamera", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    startCamera = ((JSONObject) args[0]).getBoolean("start");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }).on("repositionPlatform", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject repositionedPlatform = (JSONObject) args[0];
                try {
                    int i = repositionedPlatform.getInt("index");
                    float x = ((Double) repositionedPlatform.getDouble("x")).floatValue();
                    float y = ((Double) repositionedPlatform.getDouble("y")).floatValue();
                    int width = repositionedPlatform.getInt("width");
                    platformPositionArray.set(i, new Vector2(x, y));
                    platformWidthArray.set(i, width);
                    toRepositionIndex = i;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}