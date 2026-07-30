package com.astune.gyromancy.client.effect;

import org.junit.jupiter.api.Test;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRayEffectsTest {

    @Test
    void compilationEffectsAtSameGeometryRemainOwnedByTheirArrayLifecycle() {
        ClientRayEffects.clearAll();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Vec3 center = new Vec3(1.0, 2.0, 3.0);
        byte[] mask = {1};

        ClientRayEffects.spawnForLifecycle(
                first, 0, center, Direction.UP, new Vec3(0.0, 1.0, 0.0),
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                mask, 1, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 200, 1.0);
        ClientRayEffects.spawnForLifecycle(
                second, 0, center, Direction.UP, new Vec3(0.0, 1.0, 0.0),
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                mask, 1, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 200, 1.0);

        assertEquals(1, ClientRayEffects.activeLifecycleEffectCount(first));
        assertEquals(1, ClientRayEffects.activeLifecycleEffectCount(second));

        ClientRayEffects.stopLifecycle(first);

        assertEquals(0, ClientRayEffects.activeLifecycleEffectCount(first));
        assertEquals(1, ClientRayEffects.activeLifecycleEffectCount(second));
        ClientRayEffects.clearAll();
    }

    @Test
    void compactMeshReturnsSharedEmptyForBlankLayer() {
        ClientRayEffects.MeshVertex[] pixels = ClientRayEffects.compactMesh(new byte[4], 2, 2, symbol -> 0xFFFFFFFF);

        assertSame(pixels, ClientRayEffects.compactMesh(null, 2, 2, symbol -> 0xFFFFFFFF));
    }

    @Test
    void compactMeshSkipsInternalFacesBetweenAdjacentPixels() {
        byte[] layer = {
                1, 1
        };

        ClientRayEffects.MeshVertex[] mesh = ClientRayEffects.compactMesh(layer, 2, 1, symbol -> 0xFFFFFFFF);

        assertEquals(40, mesh.length);
    }

    @Test
    void projectionPlaneClearsWholeSourceFaceAtObliqueAngles() {
        Vec3 center = new Vec3(0, 0, 0);
        Vec3 normal = new Vec3(0, 1, 0);
        Vec3 camera = new Vec3(10, 2, 0);

        ClientRayEffects.Projection projection = ClientRayEffects.projectionPlane(center, normal, normal, camera);

        Vec3[] corners = {
                new Vec3(-0.5, 0, -0.5),
                new Vec3(0.5, 0, -0.5),
                new Vec3(0.5, 0, 0.5),
                new Vec3(-0.5, 0, 0.5)
        };
        for (Vec3 corner : corners) {
            double signedDistance = corner.subtract(projection.center()).dot(projection.normal());
            assertTrue(signedDistance <= -0.009, "corner must stay behind projection plane: " + signedDistance);
        }
    }

    @Test
    void projectionKeepsBeamHeightInWorldDirection() {
        ClientRayEffects.Projection projection = ClientRayEffects.projectionPlane(
                new Vec3(0, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 1, 0), new Vec3(10, 2, 0));

        assertEquals(0, projection.beamWorldX(), 1e-9);
        assertEquals(1.5, projection.beamWorldY(), 1e-9);
        assertEquals(0, projection.beamWorldZ(), 1e-9);
    }

    @Test
    void projectionUsesRequestedBeamHeight() {
        ClientRayEffects.Projection projection = ClientRayEffects.projectionPlane(
                new Vec3(0, 0, 0), new Vec3(0, 1, 0),
                new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                new Vec3(0, 1, 0), new Vec3(10, 2, 0), 3.0);

        assertEquals(3.0, projection.beamWorldY(), 1e-9);
    }

    @Test
    void alphaFadeCanRampIn() {
        assertEquals(0.5f, ClientRayEffects.alphaFade(5, 0, 40, 10), 1e-6f);
        assertEquals(1f, ClientRayEffects.alphaFade(10, 0, 40, 10), 1e-6f);
    }

    @Test
    void alphaFadeUsesRefreshAgeOnlyForFadeOut() {
        assertEquals(1f, ClientRayEffects.alphaFade(20, 0, 40, 10), 1e-6f);
        assertEquals(0.5f, ClientRayEffects.alphaFade(20, 20, 40, 10), 1e-6f);
    }

    @Test
    void alphaFadeKeepsOldBehaviorByDefault() {
        assertEquals(0.5f, ClientRayEffects.alphaFade(20, 20, 40, ClientRayEffects.DEFAULT_FADE_IN_TICKS), 1e-6f);
    }

    @Test
    void projectionKeepsSourceBasisOnOriginalFace() {
        ClientRayEffects.Projection projection = ClientRayEffects.projectionPlane(
                new Vec3(0, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 1, 0), new Vec3(10, 2, 0));

        assertEquals(0, projection.sourceU().y, 1e-9);
        assertEquals(0, projection.sourceV().y, 1e-9);
    }

    @Test
    void projectionUsesCanvasBasisOnVerticalFaces() {
        ClientRayEffects.Projection projection = ClientRayEffects.projectionPlane(
                new Vec3(0, 0, 0), new Vec3(1, 0, 0),
                new Vec3(0, 0, 1), new Vec3(0, 1, 0),
                new Vec3(1, 0, 0), new Vec3(4, 2, 0));

        assertEquals(0, projection.sourceU().y, 1e-9);
        assertEquals(1, projection.sourceV().y, 1e-9);
    }
}
