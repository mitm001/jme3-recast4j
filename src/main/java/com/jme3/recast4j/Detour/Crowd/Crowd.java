package com.jme3.recast4j.Detour.Crowd;

import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.recast4j.Detour.BetterDefaultQueryFilter;
import com.jme3.recast4j.Detour.DetourUtils;
import com.jme3.scene.Spatial;
import org.recast4j.detour.*;
import org.recast4j.detour.crowd.CrowdAgent;
import org.recast4j.detour.crowd.CrowdAgentParams;
import org.recast4j.detour.crowd.debug.CrowdAgentDebugInfo;

import java.lang.reflect.Field;
import java.util.function.IntFunction;

/**
 * TODO: Javadoc
 * Important to know: Filters have to be done using the queryFilterFactory. If none is specified, the first filter
 * (index 0) will be the {@link BetterDefaultQueryFilter} and all others will be filled with {@link DefaultQueryFilter}s
 */
public class Crowd extends org.recast4j.detour.crowd.Crowd {
    protected boolean debug;
    protected CrowdAgentDebugInfo debugInfo;
    protected MovementApplicationType applicationType;
    protected ApplyFunction applyFunction;
    protected Spatial[] spatialMap;
    protected TargetProximityDetector proximityDetector;
    protected FormationHandler formationHandler;
    protected NavMeshQuery m_navquery;
    protected Vector3f[] formationTargets;

    public Crowd(MovementApplicationType applicationType, int maxAgents, float maxAgentRadius, NavMesh nav)
            throws NoSuchFieldException, IllegalAccessException {
        this(applicationType, maxAgents, maxAgentRadius, nav,
                i -> (i == 0 ? new BetterDefaultQueryFilter() : new DefaultQueryFilter())
        );
    }

    public Crowd(MovementApplicationType applicationType, int maxAgents, float maxAgentRadius, NavMesh nav,
                 IntFunction<QueryFilter> queryFilterFactory) throws NoSuchFieldException, IllegalAccessException {
        super(maxAgents, maxAgentRadius, nav, queryFilterFactory);
        this.applicationType = applicationType;
        spatialMap = new Spatial[maxAgents];
        proximityDetector = new SimpleTargetProximityDetector(1f);
        formationHandler = new CircleFormationHandler(maxAgents, this, 1f);
        formationTargets = new Vector3f[maxAgents];

        Field f = getClass().getSuperclass().getDeclaredField("m_navquery");
        f.setAccessible(true);
        m_navquery = (NavMeshQuery)f.get(this);
    }

    public void setApplicationType(MovementApplicationType applicationType) {
        this.applicationType = applicationType;
    }

    public void setCustomApplyFunction(ApplyFunction applyFunction) {
        this.applyFunction = applyFunction;
    }

    public MovementApplicationType getApplicationType() {
        return applicationType;
    }

    public FormationHandler getFormationHandler() {
        return formationHandler;
    }

    /**
     * Sets the Handler which will move the agents into formation when they are close to the target.<br>
     * Passing null will disable formation.
     * @param formationHandler The handler to use
     */
    public void setFormationHandler(FormationHandler formationHandler) {
        this.formationHandler = formationHandler;
    }

    public void update(float deltaTime) {
        if (debug) {
            debugInfo = new CrowdAgentDebugInfo(); // Clear.
            update(deltaTime, debugInfo);
        } else {
            update(deltaTime, null);
        }
    }

    @Override
    public CrowdAgent getAgent(int idx) {
        CrowdAgent ca = super.getAgent(idx);
        if (ca == null) {
            throw new IndexOutOfBoundsException("Invalid Index");
        }

        return ca;
    }

    public CrowdAgent createAgent(Vector3f pos, CrowdAgentParams params) {
        int idx = addAgent(DetourUtils.toFloatArray(pos), params);
        if (idx == -1) {
            throw new IndexOutOfBoundsException("This crowd doesn't have a free slot anymore.");
        }
        return super.getAgent(idx);
    }

    /**
     * Call this method to update the internal data storage of spatials.
     * This is required for some {@link MovementApplicationType}s.
     * @param agent The Agent
     * @param spatial The Agent's Spatial
     */
    public void setSpatialForAgent(CrowdAgent agent, Spatial spatial) {
        spatialMap[agent.idx] = spatial;
    }

    /**
     * Remove the Agent from this Crowd (Convenience Wrapper around {@link #removeAgent(int)})
     * @param agent The Agent to remove from the crowd
     */
    public void removeAgent(CrowdAgent agent) {
        if (agent.idx != -1) {
            removeAgent(agent.idx);
        }
    }

    /**
     * This method is called by the CrowdManager to move the agents on the screen.
     */
    protected void applyMovements() {
        getActiveAgents().stream().filter(this::isMoving)
            .forEach(ca -> applyMovement(ca, DetourUtils.createVector3f(ca.npos),
                            DetourUtils.createVector3f(ca.vel)));
    }

    protected void applyMovement(CrowdAgent crowdAgent, Vector3f newPos, Vector3f velocity) {
        switch (applicationType) {
            case NONE:
                break;

            case CUSTOM:
                applyFunction.applyMovement(crowdAgent, newPos, velocity);
                break;

            case DIRECT:
                // Debug Code to handle "approaching behavior"
                System.out.println("" + Boolean.toString(crowdAgent.targetRef != 0) + " speed: " + velocity.length() + " newPos: " + newPos + " velocity: " + velocity);
                if (velocity.length() > 0.1f) {
                    Quaternion rotation = new Quaternion();
                    rotation.lookAt(velocity.normalize(), Vector3f.UNIT_Y);
                    spatialMap[crowdAgent.idx].setLocalTranslation(newPos);
                    spatialMap[crowdAgent.idx].setLocalRotation(rotation);
                }
                break;

            case BETTER_CHARACTER_CONTROL:
                BetterCharacterControl bcc = spatialMap[crowdAgent.idx].getControl(BetterCharacterControl.class);
                bcc.setWalkDirection(velocity);
                bcc.setViewDirection(velocity.normalize());

                /* Note: Unfortunately BetterCharacterControl does not expose getPhysicsLocation but it's tied to the
                 * SceneGraph Position
                 */
                if (SimpleTargetProximityDetector.euclideanDistanceSquared(newPos,
                        spatialMap[crowdAgent.idx].getWorldTranslation()) > 0.4f * 0.4f) {
                    /* Note: This should never occur but when collisions happen, they happen. Let's hope we can get away
                     * with that even though DtCrowd documentation explicitly states that one should not move agents
                     * constantly (okay, we only do it in rare cases, but still). Bugs could appear when some internal
                     * state is voided. The most clean solution would be removeAgent(), addAgent() but that has some
                     * overhead as well as possibly messing with the index one which some 3rd-party code might rely on.
                      */
                    System.out.println("Resetting Agent because of physics drift");
                    DetourUtils.fillFloatArray(crowdAgent.npos, spatialMap[crowdAgent.idx].getWorldTranslation());
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown Application Type");
        }

        // If we aren't currently forming.
        if (formationTargets[crowdAgent.idx] == null) {
            if (proximityDetector.isInTargetProximity(crowdAgent, newPos,
                    DetourUtils.createVector3f(crowdAgent.targetPos))) {
                // Handle Crowd Agent in proximity.
                if (formationHandler != null) {
                    resetMoveTarget(crowdAgent.idx); // Make him stop moving.
                    formationTargets[crowdAgent.idx] = formationHandler.moveIntoFormation(crowdAgent);
                    // It's up to moveIntoFormation to make the agent move, we could however also design the API so we just
                    // use the return value for this. Then it would be less prone to user error. On the other hand the
                    // "do" something pattern is more implicative than "getFormationPosition"
                } else {
                    resetMoveTarget(crowdAgent.idx); // Make him stop moving.
                }

            } else {
                System.out.println("not in proximity of " + DetourUtils.createVector3f(crowdAgent.targetPos));
                // @TODO: Stuck detection?
            }
        } else {
            if (SimpleTargetProximityDetector.euclideanDistanceSquared(newPos,
                    formationTargets[crowdAgent.idx]) < 0.1f * 0.1f) {
                resetMoveTarget(crowdAgent.idx); // does formationTargets[crowdAgent.idx] = null; for us
                System.out.println("Reached Target");
            } else {
                System.out.println("IS FORMING");
            }
        }
    }

    /**
     * Makes the whole Crowd move to a target. Know that you can also move individual agents.
     * @param to The Move Target
     * @param polyRef The Polygon to which the target belongs
     * @return Whether all agents could be scheduled to approach the target
     * @deprecated Will be removed because specifying the polyRef is undesired (as crashes happen
     * when this value is wrong (e.g. not taken the Filters into account)).
     */
    @Deprecated
    protected boolean requestMoveToTarget(Vector3f to, long polyRef) {
        if (polyRef == 0 || to == null) {
            throw new IllegalArgumentException("Invalid Target (" + to + ", " + polyRef + ")");
        }

        if (formationHandler != null) {
            formationHandler.setTargetPosition(to);
        }

        // Unfortunately ag.setTarget is not an exposed API, maybe we'll write a dispatcher class if that bugs me too much
        // Why? That way we could throw Exceptions when the index is wrong (IndexOutOfBoundsEx)
        return getActiveAgents().stream()
                .allMatch(ca -> requestMoveTarget(ca.idx, polyRef, DetourUtils.toFloatArray(to)));

    }

    /**
     * Makes the whole Crowd move to a target. Know that you can also move individual agents.
     * @param to The Move Target
     * @return Whether all agents could be scheduled to approach the target
     * @see #requestMoveToTarget(CrowdAgent, Vector3f)
     */
    public boolean requestMoveToTarget(Vector3f to) {
        if (formationHandler != null) {
            formationHandler.setTargetPosition(to);
        }
        return getActiveAgents().stream().allMatch(ca -> requestMoveToTarget(ca, to));
        // if all were successful, return true, else return false.
    }

    /**
     * Moves a specified Agent to a Location.<br />
     * This code implicitly searches for the correct polygon with a constant tolerance, in most cases you should prefer
     * to determine the poly ref manually with domain specific knowledge.
     * @see #requestMoveToTarget(CrowdAgent, long, Vector3f)
     * @param crowdAgent the agent to move
     * @param to where the agent shall move to
     * @return whether this operation was successful
     */
    public boolean requestMoveToTarget(CrowdAgent crowdAgent, Vector3f to) {
        Result<FindNearestPolyResult> res = m_navquery.findNearestPoly(DetourUtils.toFloatArray(to), getQueryExtents(),
                getFilter(crowdAgent.params.queryFilterType));

        if (res.status.isSuccess() && res.result.getNearestRef() != -1) {
            return requestMoveTarget(crowdAgent.idx, res.result.getNearestRef(), DetourUtils.toFloatArray(to));
        } else {
            return false;
        }
    }

    /**
     * Moves a specified Agent to a Location.
     * @param crowdAgent the agent to move
     * @param polyRef The Polygon where the position resides
     * @param to where the agent shall move to
     * @return whether this operation was successful
     * @deprecated Use non-polRef instead
     */
    @Deprecated
    protected boolean requestMoveToTarget(CrowdAgent crowdAgent, long polyRef, Vector3f to) {
        return requestMoveTarget(crowdAgent.idx, polyRef, DetourUtils.toFloatArray(to));
    }

    @Override
    public boolean requestMoveTarget(int idx, long ref, float[] pos) {
        formationTargets[idx] = null; // Reset formation state.
        return super.requestMoveTarget(idx, ref, pos);
    }

    @Override
    public boolean resetMoveTarget(int idx) {
        formationTargets[idx] = null;
        return super.resetMoveTarget(idx);
    }

    /**
     * When the Agent is ACTIVE and moving (has a valid target set).
     * @param crowdAgent The agent to query
     * @return If the agent is moving
     */
    public boolean isMoving(CrowdAgent crowdAgent) {
        return crowdAgent.active && crowdAgent.targetState == CrowdAgent.MoveRequestState.DT_CROWDAGENT_TARGET_VALID;
    }

    /**
     * When the Agent is ACTIVE and has no target (this is not the same as !{@link #isMoving(CrowdAgent)}).
     * @param crowdAgent The agent to query
     * @return If the agent has no target
     */
    public boolean hasNoTarget(CrowdAgent crowdAgent) {
        return crowdAgent.active && crowdAgent.targetState == CrowdAgent.MoveRequestState.DT_CROWDAGENT_TARGET_NONE;
    }

    /**
     * When the Agent is ACTIVE and moving into a formation (which means he is close enough to his target, by the means
     * of {@link TargetProximityDetector#isInTargetProximity(CrowdAgent, Vector3f, Vector3f)}
     * @param crowdAgent The Agent to query
     * @return If the Agent is forming
     */
    public boolean isForming(CrowdAgent crowdAgent) {
        return crowdAgent.active && formationTargets[crowdAgent.idx] != null;
    }
}
