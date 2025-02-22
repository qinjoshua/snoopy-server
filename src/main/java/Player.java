package main.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The agents in the game: rockets with physics associated with them.
 */
public class Player {
    /**
     * The position of the player.
     */
    GamePosn posn;

    /**
     * The velocity of the player.
     */
    GameVector velocity;

    /**
     * The acceleration of the player not due to gravity.
     */
    GameVector accel;

    /**
     * The orientation of the player, in radians. This is the direction the thrust moves the player, so 0 means the
     * player is facing right.
     */
    double orientation;

    /**
     * Whether the thrusters are active or not.
     */
    boolean thrustersOn;

    /**
     * The angular velocity in radians per game second.
     */
    double angularVelocity;

    /**
     * The game seconds until the player can fire again, or 0 if the player can fire at will.
     */
    double cooldown;

    /**
     * The ID number of the player, which should be unique within a single game.
     */
    int id;

    /**
     * Creates a player at the given position and orientation with zero velocity and acceleration, thrusters off, and
     * no cooldown on firing.
     */
    public Player(GamePosn posn, double orientation, int id) {
        this.posn = posn;
        this.orientation = orientation;
        this.id = id;
        this.velocity = GameVector.origin();
        this.accel = GameVector.origin();
        this.thrustersOn = false;
        this.angularVelocity = 0;
        this.cooldown = 0;
    }

    public ABody getBody(GameConfig config) {
        return new Rect(this.posn, config.getPlayerRadius(), config.getPlayerRadius());
    }

    /**
     * Updates the location and state of the player for the given length of time. No intermediate calculation is
     * done, so the physics will become less and less accurate as dt increases.
     *
     * @param dt the delta to compute time for, in game seconds
     * @param model the physics model to use for the calculations
     */
    public void move(double dt, PhysicsModel model) {
        GameVector thrust;
        if (this.thrustersOn) {
            thrust = model.computeThrust(this.orientation);
        } else {
            thrust = GameVector.origin();
        }

        this.posn = posn.addVector(this.velocity.scale(dt));
        this.orientation = (this.orientation + dt * this.angularVelocity) % (Math.PI * 2);

        GameVector drag = model.computeDrag(this.velocity);

        GameVector realAccel = this.accel.add(model.getGravity());
        this.velocity = this.velocity.add(realAccel.scale(dt));

        this.accel = this.accel.add(drag.scale(dt)).add(thrust.scale(dt));

        // thrusters and angular velocity remain unchanged

        // update cooldown
        this.cooldown += dt;
    }

    public void fireThrusters() {
        this.thrustersOn = true;
    }

    public void stopThrusters() {
        this.thrustersOn = false;
    }

    /**
     * Serializes the object to a JSON string in the expected format.
     */
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.add("position", this.posn.toJson());
        json.add("velocity", this.velocity.toJson());
        json.add("acceleration", this.accel.toJson());
        json.addProperty("orientation", this.orientation);
        json.addProperty("cooldown", this.cooldown);
        return json;
    }

    /**
     * With the given list of actions, advances this player's state by the given amount of time. The input GameState
     * is used to fire bullets, but dying is handled outside of this class.
     */
    public void takeActions(Iterable<Action> actions, GameState state, double dt) {
        this.reset();
        for (Action a : actions) {
            this.takeAction(a, state, dt);
        }

        // now move forward with the updated state
        this.move(dt, state.getPhysics());
    }

    public void takeAction(Action action, GameState state, double dt) {
        /*
        So the big question is: why not do this through double dispatch? I considered making Action a full-blown
        interface with four implementing classes, which has the virtue of being able to add more actions quickly. I
        think that this is ultimately better, however, because the exact mechanics of player movement vary more than
        the set of actions the player can take. This ensures that the Player class retains full control over the
        physics and game control.
         */
        switch (action) {
            case BankLeft:
                this.bankLeft(state);
            case BankRight:
                this.bankRight(state);
            case Thrust:
                this.fireThrusters();
            case Fire:
                this.fire(state);
        }
    }

    public void fire(GameState state) {
        double maxCooldown = state.getConfig().getBulletCooldown();
        if (this.cooldown > maxCooldown) {
            this.cooldown = 0;
            state.fire(this.id, this.posn, this.orientation);
        }
    }

    public void bankLeft(GameState state) {
        this.angularVelocity = state.getConfig().getTurnSpeed();
    }

    public void bankRight(GameState state) {
        this.angularVelocity = -state.getConfig().getTurnSpeed();
    }

    /**
     * Resets the angular velocity and thrusters to a motionless state.
     */
    public void reset() {
        this.angularVelocity = 0;
        this.stopThrusters();
    }
}
