package com.astune.gyromancy.client.effect;

import com.astune.gyromancy.api.geometry.SurfaceFrame;
import org.junit.jupiter.api.Test;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
    void compilationEffectRetainsAnArbitrarySourceSurfaceNormal() {
        ClientRayEffects.clearAll();
        UUID lifecycle = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Vec3 normal = new Vec3(1.0, 1.0, 1.0).normalize();
        Vec3 sourceU = new Vec3(1.0, -1.0, 0.0).normalize();
        Vec3 sourceV = normal.cross(sourceU).normalize();

        ClientRayEffects.spawnForLifecycle(
                lifecycle, 0, Vec3.ZERO, normal, normal, sourceU, sourceV,
                new byte[]{1}, 1, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 200, 1.0);

        assertEquals(normal, ClientRayEffects.lifecycleSourceNormal(lifecycle, 0).orElseThrow());
        ClientRayEffects.clearAll();
    }

    @Test
    void compilationEffectFollowsRefreshedArrayGeometryDuringFadeIn() {
        ClientRayEffects.clearAll();
        UUID lifecycle = UUID.fromString("00000000-0000-0000-0000-000000000004");
        byte[] mask = {1};
        Vec3 initialCenter = new Vec3(1.0, 2.0, 3.0);
        Vec3 movedCenter = new Vec3(4.0, 5.0, 6.0);
        Vec3 movedNormal = new Vec3(1.0, 0.0, 0.0);

        ClientRayEffects.spawnForLifecycle(
                lifecycle, 0, initialCenter, Direction.NORTH, new Vec3(0.0, 0.0, -1.0),
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0),
                mask, 1, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 200, 1.0);
        ClientRayEffects.spawnForLifecycle(
                lifecycle, 0, movedCenter, movedNormal, movedNormal,
                new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 1.0, 0.0),
                mask, 1, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 199, 1.0);

        assertEquals(movedCenter, ClientRayEffects.lifecycleCenter(lifecycle, 0).orElseThrow());
        assertEquals(movedNormal, ClientRayEffects.lifecycleSourceNormal(lifecycle, 0).orElseThrow());
        ClientRayEffects.clearAll();
    }

    @Test
    void lifecycleEffectCanBindToItsProjectionEntity() {
        ClientRayEffects.clearAll();
        UUID lifecycle = UUID.fromString("00000000-0000-0000-0000-000000000005");

        ClientRayEffects.spawnForLifecycle(
                lifecycle, 0, Vec3.ZERO, Direction.NORTH, new Vec3(0.0, 0.0, -1.0),
                new Vec3(2.0, 0.0, 0.0), new Vec3(0.0, 3.0, 0.0),
                new byte[]{1}, 1, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 200, 1.0);
        ClientRayEffects.bindLifecycleSource(lifecycle, 0, 42);

        assertEquals(42, ClientRayEffects.lifecycleSourceEntityId(lifecycle, 0).orElseThrow());
        ClientRayEffects.clearAll();
    }

    @Test
    void projectionEffectUsesTheRenderedSurfaceFrameWithoutChangingItsSize() {
        Vec3 center = new Vec3(4.0, 5.0, 6.0);
        SurfaceFrame frame = SurfaceFrame.facing(
                center, new Vec3(1.0, 0.0, 1.0), new Vec3(0.0, 1.0, 0.0));

        ClientRayEffects.EffectGeometry geometry =
                ClientRayEffects.projectionGeometry(frame, 2.0, 3.0);

        assertEquals(center, geometry.center());
        assertEquals(frame.normal(), geometry.normal());
        assertEquals(frame.normal(), geometry.worldRayDir());
        assertEquals(frame.axisU(), geometry.sourceU().normalize());
        assertEquals(frame.axisV(), geometry.sourceV().normalize());
        assertEquals(2.0, geometry.sourceU().length(), 1e-9);
        assertEquals(3.0, geometry.sourceV().length(), 1e-9);
    }

    @Test
    void movingLifecycleKeepsItsExistingDisplayDeadline() {
        assertEquals(200, ClientRayEffects.inheritedDeadline(40, 200, 180));
    }

    @Test
    void authoritativeRemainingTimeMayShortenButNeverExtendTheDeadline() {
        assertEquals(160, ClientRayEffects.inheritedDeadline(40, 200, 120));
        assertEquals(200, ClientRayEffects.inheritedDeadline(40, 200, 300));
    }

    @Test
    void serverDeadlineExpiresIndependentlyOfRenderedFrameCount() {
        assertEquals(0.5f, ClientRayEffects.deadlineFade(1_100.0, 1_200L, 200, 0), 1e-6f);
        assertEquals(0.0f, ClientRayEffects.deadlineFade(1_200.0, 1_200L, 200, 0), 1e-6f);
        assertEquals(0.0f, ClientRayEffects.deadlineFade(1_250.0, 1_200L, 200, 0), 1e-6f);
    }

    @Test
    void compactMeshReturnsSharedEmptyForBlankLayer() {
        ClientRayEffects.MeshVertex[] pixels = ClientRayEffects.compactMesh(new int[4], 2, 2, symbol -> 0xFFFFFFFF);

        assertSame(pixels, ClientRayEffects.compactMesh(null, 2, 2, symbol -> 0xFFFFFFFF));
    }

    @Test
    void compactMeshSkipsInternalFacesBetweenAdjacentPixels() {
        int[] layer = {
                1, 1
        };

        ClientRayEffects.MeshVertex[] mesh = ClientRayEffects.compactMesh(layer, 2, 1, symbol -> 0xFFFFFFFF);

        assertEquals(40, mesh.length);
    }

    @Test
    void geometryMeshRebuildsWhenLayerChangesDuringFadeIn() {
        ClientRayEffects.clearAll();
        Vec3 center = new Vec3(1.0, 2.0, 3.0);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("test", "same_texture");

        ClientRayEffects.spawnOrRefresh(
                center, Direction.UP, new Vec3(0.0, 1.0, 0.0),
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                texture, new int[]{1, 0}, 2, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 40, 0.3, 20);
        ClientRayEffects.spawnOrRefresh(
                center, Direction.UP, new Vec3(0.0, 1.0, 0.0),
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                texture, new int[]{1, 1}, 2, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 40, 0.3, 20);

        assertEquals(40, ClientRayEffects.geometryMeshVertexCount(center, Direction.UP));
        ClientRayEffects.clearAll();
    }

    @Test
    void geometryMeshDoesNotUseTextureIdentityAsContentVersion() {
        ClientRayEffects.clearAll();
        Vec3 center = new Vec3(1.0, 2.0, 3.0);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("test", "same_texture");

        ClientRayEffects.spawnOrRefresh(
                center, Direction.UP, new Vec3(0.0, 1.0, 0.0),
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                texture, new int[]{1, 0}, 2, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 40, 0.3, 0);
        ClientRayEffects.spawnOrRefresh(
                center, Direction.UP, new Vec3(0.0, 1.0, 0.0),
                new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                texture, new int[]{1, 1}, 2, 1, ignored -> 0xFFFFFFFF,
                0xFFFFFFFF, 40, 0.3, 0);

        assertEquals(40, ClientRayEffects.geometryMeshVertexCount(center, Direction.UP));
        ClientRayEffects.clearAll();
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
