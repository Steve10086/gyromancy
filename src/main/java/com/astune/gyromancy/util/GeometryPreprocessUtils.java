package com.astune.gyromancy.util;

import java.util.*;

/**
 * Full skeleton-extraction and skeleton-graph pipeline.
 *
 * <h3>Image pipeline</h3>
 * <pre>
 *   raw → fillSmallHoles → upscaleConnectivityPreserving(≥128)
 *       → gaussianSmoothBinary(σ=K/3) → skeletonize()
 * </pre>
 *
 * <h3>Graph pipeline</h3>
 * <pre>
 *   skeleton → extractNodes → traceEdges → mergeZeroEdges
 *            → pruneShortBranches → (result: nodes + edges)
 * </pre>
 */
public final class GeometryPreprocessUtils {

    private GeometryPreprocessUtils() {}

    // ═══════════════════════ Image preprocess ═══════════════════════

    /**
     * Fills enclosed background holes whose area is below {@code max(5, max(w,h)/10)}.
     */
    public static int[][] fillSmallHoles(int[][] src) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0) return new int[0][0];
        int areaThreshold = Math.min(5, Math.max(w, h) / 10);
        int[][] dst = new int[h][w];
        for (int y = 0; y < h; y++) System.arraycopy(src[y], 0, dst[y], 0, w);
        boolean[][] visited = new boolean[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (dst[y][x] != 0 || visited[y][x]) continue;
                ArrayDeque<int[]> q = new ArrayDeque<>();
                ArrayList<int[]> region = new ArrayList<>();
                q.add(new int[]{x, y}); visited[y][x] = true;
                boolean touchesBorder = false;
                while (!q.isEmpty()) {
                    int[] p = q.poll(); int cx = p[0], cy = p[1]; region.add(p);
                    if (cx == 0 || cx == w - 1 || cy == 0 || cy == h - 1) touchesBorder = true;
                    if (cx > 0 && dst[cy][cx - 1] == 0 && !visited[cy][cx - 1]) { visited[cy][cx - 1] = true; q.add(new int[]{cx - 1, cy}); }
                    if (cx + 1 < w && dst[cy][cx + 1] == 0 && !visited[cy][cx + 1]) { visited[cy][cx + 1] = true; q.add(new int[]{cx + 1, cy}); }
                    if (cy > 0 && dst[cy - 1][cx] == 0 && !visited[cy - 1][cx]) { visited[cy - 1][cx] = true; q.add(new int[]{cx, cy - 1}); }
                    if (cy + 1 < h && dst[cy + 1][cx] == 0 && !visited[cy + 1][cx]) { visited[cy + 1][cx] = true; q.add(new int[]{cx, cy + 1}); }
                }
                if (!touchesBorder && region.size() < areaThreshold)
                    for (int[] p : region) dst[p[1]][p[0]] = 1;
            }
        return dst;
    }

    /**
     * Upscales a binary image to at least {@code minSize} in both dimensions
     * while preserving 8-connectivity via K×K blocks + diagonal bridging.
     */
    public static int[][] upscaleConnectivityPreserving(int[][] src, int minSize) {
        int sh = src.length, sw = sh > 0 ? src[0].length : 0;
        if (sw == 0 || sh == 0) return new int[minSize][minSize];
        int minDim = Math.min(sw, sh);
        int K = (minSize + minDim - 1) / minDim; if (K < 1) K = 1;
        int dw = sw * K + 1, dh = sh * K + 1;
        int[][] dst = new int[dh][dw];
        for (int oy = 0; oy < sh; oy++) { int y0 = oy * K;
            for (int ox = 0; ox < sw; ox++) { if (src[oy][ox] == 0) continue; int x0 = ox * K;
                for (int dy = 0; dy < K; dy++) for (int dx = 0; dx < K; dx++) dst[y0 + dy][x0 + dx] = 1;
            }
        }
        for (int oy = 0; oy < sh; oy++)
            for (int ox = 0; ox < sw; ox++) { if (src[oy][ox] == 0) continue;
                if (ox + 1 < sw && oy + 1 < sh && src[oy + 1][ox + 1] != 0) { int cx = ox*K+K-1, cy = oy*K+K-1; dst[cy][cx+1] = 1; dst[cy+1][cx] = 1; }
                if (ox - 1 >= 0 && oy + 1 < sh && src[oy + 1][ox - 1] != 0) { int cx = ox*K, cy = oy*K+K-1; dst[cy][cx-1] = 1; dst[cy+1][cx] = 1; }
            }
        return dst;
    }

    /** Separable Gaussian blur + threshold at 0.5. */
    public static int[][] gaussianSmoothBinary(int[][] src, double sigma) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0 || sigma <= 0) { int[][] cp = new int[h][w]; for (int y=0;y<h;y++) System.arraycopy(src[y],0,cp[y],0,w); return cp; }
        float[][] buf = new float[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) buf[y][x] = src[y][x];
        int r = (int) Math.ceil(3.0 * sigma);
        double[] kernel = new double[2 * r + 1]; double s2 = 2.0 * sigma * sigma; double ksum = 0;
        for (int i = -r; i <= r; i++) { kernel[i + r] = Math.exp(-i * i / s2); ksum += kernel[i + r]; }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= ksum;
        float[][] hPass = new float[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) { double sum=0; for (int i=-r;i<=r;i++){ int sx=x+i; if(sx<0)sx=0;else if(sx>=w)sx=w-1; sum+=buf[y][sx]*kernel[i+r]; } hPass[y][x]=(float)sum; }
        int[][] dst = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) { double sum=0; for (int i=-r;i<=r;i++){ int sy=y+i; if(sy<0)sy=0;else if(sy>=h)sy=h-1; sum+=hPass[sy][x]*kernel[i+r]; } dst[y][x]=sum>=0.5?1:0; }
        return dst;
    }

    // ═══════════ Guo-Hall Skeletonization ═══════════

    private static final int[][] SKEL_MASK = {{8,4,2},{16,0,1},{32,64,128}};
    private static final boolean[] G123_LUT  = makeG123Lut();
    private static final boolean[] G123P_LUT = makeG123PLut();

    /** Guo & Hall (1989) thinning — pixel-identical to skimage.morphology.thin(). */
    public static int[][] skeletonize(int[][] src) {
        int h = src.length, w = h > 0 ? src[0].length : 0;
        if (w == 0 || h == 0) return new int[0][0];
        boolean[][] a = new boolean[h + 2][w + 2], b = new boolean[h + 2][w + 2];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) a[y + 1][x + 1] = src[y][x] != 0;
        boolean changed;
        do { changed = false;
            copy(b, a, h, w);
            for (int y = 1; y <= h; y++) for (int x = 1; x <= w; x++) if (a[y][x] && G123_LUT[encode(a, x, y)]) { b[y][x]=false; changed=true; }
            swapRows(a, b);
            copy(b, a, h, w);
            for (int y = 1; y <= h; y++) for (int x = 1; x <= w; x++) if (a[y][x] && G123P_LUT[encode(a, x, y)]) { b[y][x]=false; changed=true; }
            swapRows(a, b);
        } while (changed);
        int[][] out = new int[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (a[y + 1][x + 1]) out[y][x] = 1;
        return out;
    }

    private static void copy(boolean[][] dst, boolean[][] src, int h, int w) { for (int y=0;y<=h+1;y++) System.arraycopy(src[y],0,dst[y],0,w+2); }
    private static void swapRows(boolean[][] x, boolean[][] y) { for (int r=0;r<x.length;r++){boolean[] t=x[r];x[r]=y[r];y[r]=t;} }
    private static int encode(boolean[][] m, int x, int y) { int idx=0; for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(m[y+dy][x+dx])idx+=SKEL_MASK[dy+1][dx+1]; return idx; }

    private static boolean[] makeG123Lut()  { boolean[] l=new boolean[256]; for(int n=0;n<256;n++)l[n]=g1(n)&&g2(n)&&g3(n); return l; }
    private static boolean[] makeG123PLut() { boolean[] l=new boolean[256]; for(int n=0;n<256;n++)l[n]=g1(n)&&g2(n)&&g3p(n); return l; }
    private static boolean g1(int n) { int s=0; if(!bh(n,0)&&(bh(n,1)||bh(n,2)))s++; if(!bh(n,2)&&(bh(n,3)||bh(n,4)))s++; if(!bh(n,4)&&(bh(n,5)||bh(n,6)))s++; if(!bh(n,6)&&(bh(n,7)||bh(n,0)))s++; return s==1; }
    private static boolean g2(int n) { int n1=0,n2=0; for(int k:new int[]{1,3,5,7}){if(bh(n,k)||bh(n,k-1))n1++;if(bh(n,k)||bh(n,(k+1)%8))n2++;} int m=Math.min(n1,n2); return m==2||m==3; }
    private static boolean g3(int n)  { return !((bh(n,1)||bh(n,2)||!bh(n,7))&&bh(n,0)); }
    private static boolean g3p(int n) { return !((bh(n,5)||bh(n,6)||!bh(n,3))&&bh(n,4)); }
    private static boolean bh(int n, int i) { return (n>>i&1)==1; }

    // ═══════════ Skeleton Graph ═══════════

    public record SkelNode(int id, int x, int y, int degree, boolean isEndpoint) {}
    public record SkelEdge(int from, int to, List<int[]> path) { public int length() { return path.size(); } }

    /** Extracts endpoints (deg=1) and junctions (deg≥3) via 8-neighbor convolution. */
    public static List<SkelNode> extractNodes(int[][] skel) {
        int h = skel.length, w = h > 0 ? skel[0].length : 0;
        List<SkelNode> nodes = new ArrayList<>(); int id = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (skel[y][x] != 0) { int deg = countNeighbors8(skel, x, y); if (deg != 2) nodes.add(new SkelNode(id++, x, y, deg, deg == 1)); }
        return nodes;
    }

    private static int countNeighbors8(int[][] img, int x, int y) { int h=img.length,w=img[0].length,c=0; for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){if(dx==0&&dy==0)continue;int ny=y+dy,nx=x+dx;if(ny>=0&&ny<h&&nx>=0&&nx<w&&img[ny][nx]!=0)c++;} return c; }

    /** Traces all edges between nodes along the skeleton. */
    public static List<SkelEdge> traceEdges(int[][] skel, List<SkelNode> nodes) {
        int h = skel.length, w = skel[0].length, n = nodes.size();
        if (n == 0) return List.of();
        int[][] nodeId = new int[h][w]; for (int i = 0; i < h; i++) Arrays.fill(nodeId[i], -1);
        for (SkelNode nd : nodes) nodeId[nd.y()][nd.x()] = nd.id();
        boolean[][] visited = new boolean[h][w];
        List<SkelEdge> edges = new ArrayList<>();
        for (SkelNode start : nodes) { int sx = start.x(), sy = start.y();
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) { if (dx==0&&dy==0) continue;
                int nx = sx+dx, ny = sy+dy; if (ny<0||ny>=h||nx<0||nx>=w) continue;
                if (skel[ny][nx]==0) continue;
                // Allow visited neighbours that are themselves nodes (adjacent junctions)
                if (nodeId[ny][nx] < 0 && visited[ny][nx]) continue;
                int prevX=sx, prevY=sy, cx=nx, cy=ny; List<int[]> path = new ArrayList<>(); boolean hitNode=false; int endId=-1, steps=0;
                while (steps < w*h) { path.add(new int[]{cx,cy}); visited[cy][cx]=true; steps++;
                    int nid = nodeId[cy][cx]; if (nid>=0) { endId=nid; hitNode=true; break; }
                    boolean moved=false;
                    for (int ddy=-1;ddy<=1;ddy++) for (int ddx=-1;ddx<=1;ddx++) { if(ddx==0&&ddy==0)continue;
                        int tnx=cx+ddx,tny=cy+ddy; if(tny<0||tny>=h||tnx<0||tnx>=w)continue; if(skel[tny][tnx]==0)continue; if(tnx==prevX&&tny==prevY)continue;
                        int tnid=nodeId[tny][tnx]; if(tnid>=0){path.add(new int[]{tnx,tny});visited[tny][tnx]=true;endId=tnid;hitNode=moved=true;break;}
                        if(!visited[tny][tnx]){prevX=cx;prevY=cy;cx=tnx;cy=tny;moved=true;break;}
                    } if(!moved)break; if(hitNode)break;
                } if(hitNode&&endId>=0) edges.add(new SkelEdge(start.id(),endId,path));
            }
        }
        return edges; // keep parallel edges (same node pair, different paths)
    }

    /** Merges nodes connected by an edge of length ≤ 1 (Guo-Hall stair-step artifact). */
    public static void mergeZeroEdges(List<SkelNode> nodes, List<SkelEdge> edges) {
        boolean changed = true;
        while (changed) { changed = false;
            for (int ei = 0; ei < edges.size(); ei++) { SkelEdge e = edges.get(ei); if (e.length() > 1) continue; int a = e.from(), b = e.to(); if (a==b) continue;
                SkelNode na=nodes.get(a), nb=nodes.get(b); int mx=(na.x()+nb.x())/2, my=(na.y()+nb.y())/2, md=Math.max(na.degree(),nb.degree()); boolean ep=na.isEndpoint()&&nb.isEndpoint();
                int n=nodes.size(); int[] oldToNew=new int[n]; Arrays.fill(oldToNew,-1); List<SkelNode> merged=new ArrayList<>();
                for(int i=0;i<n;i++){if(i==b)continue; oldToNew[i]=merged.size(); if(i==a) merged.add(new SkelNode(merged.size(),mx,my,md,ep)); else{SkelNode o=nodes.get(i);merged.add(new SkelNode(merged.size(),o.x(),o.y(),o.degree(),o.isEndpoint()));}}
                List<SkelEdge> remapped=new ArrayList<>();
                for(int ej=0;ej<edges.size();ej++){if(ej==ei)continue; SkelEdge oe=edges.get(ej); int nf=oldToNew[oe.from()],nt=oldToNew[oe.to()]; if(oe.from()==b)nf=oldToNew[a]; if(oe.to()==b)nt=oldToNew[a]; if(nf<0||nt<0||nf==nt)continue; remapped.add(new SkelEdge(nf,nt,oe.path()));}
                nodes.clear();nodes.addAll(merged); edges.clear();edges.addAll(remapped); changed=true; break;
            }
        }
    }

    /** Prunes short endpoint branches and collapses leftover degree-2 path nodes. */
    public static void pruneShortBranches(List<SkelNode> nodes, List<SkelEdge> edges, int minLen) {
        int n=nodes.size(); List<List<Integer>> adj=new ArrayList<>(n); for(int i=0;i<n;i++)adj.add(new ArrayList<>());
        for(int ei=0;ei<edges.size();ei++){SkelEdge e=edges.get(ei);adj.get(e.from()).add(ei);adj.get(e.to()).add(ei);}
        Set<Integer> removeEdges=new HashSet<>(),removeNodes=new HashSet<>(); boolean changed=true;
        while(changed){changed=false;
            for(int ni=0;ni<n;ni++){if(removeNodes.contains(ni))continue; SkelNode node=nodes.get(ni); if(!node.isEndpoint())continue;
                List<Integer> myEdges=adj.get(ni); int livingEdge=-1; for(int ei:myEdges)if(!removeEdges.contains(ei)){livingEdge=ei;break;} if(livingEdge<0)continue;
                SkelEdge e=edges.get(livingEdge); if(e.length()>=minLen)continue;
                removeEdges.add(livingEdge);removeNodes.add(ni);
                int otherId=(e.from()==ni)?e.to():e.from();
                int livingCount=0; for(int ei:adj.get(otherId))if(!removeEdges.contains(ei))livingCount++;
                if(livingCount==1){SkelNode updated=new SkelNode(otherId,nodes.get(otherId).x(),nodes.get(otherId).y(),1,true);nodes.set(otherId,updated);}
                changed=true;
            }
        }
        List<SkelNode> keptNodes=new ArrayList<>(); int[] oldToNew=new int[n]; Arrays.fill(oldToNew,-1);
        for(int i=0;i<n;i++)if(!removeNodes.contains(i)){oldToNew[i]=keptNodes.size(); SkelNode old=nodes.get(i); keptNodes.add(new SkelNode(oldToNew[i],old.x(),old.y(),old.degree(),old.isEndpoint()));}
        List<SkelEdge> keptEdges=new ArrayList<>();
        for(int ei=0;ei<edges.size();ei++)if(!removeEdges.contains(ei)){SkelEdge e=edges.get(ei); int nf=oldToNew[e.from()],nt=oldToNew[e.to()]; if(nf>=0&&nt>=0)keptEdges.add(new SkelEdge(nf,nt,e.path()));}
        collapseDegree2(keptNodes,keptEdges);
        nodes.clear();nodes.addAll(keptNodes); edges.clear();edges.addAll(keptEdges);
    }

    private static void collapseDegree2(List<SkelNode> nodes, List<SkelEdge> edges) {
        int n=nodes.size(); if(n==0)return; List<List<Integer>> adj=buildAdj(n,edges);
        Set<Integer> deadE=new HashSet<>(),deadN=new HashSet<>(); boolean changed=true;
        while(changed){changed=false;
            for(int xi=0;xi<n;xi++){if(deadN.contains(xi))continue; int living=0,e1=-1,e2=-1;
                for(int ei:adj.get(xi))if(!deadE.contains(ei)){if(living==0)e1=ei;else if(living==1){e2=ei;break;}living++;} if(living!=2)continue;
                SkelNode xn=nodes.get(xi); if(xn.degree()>=4)continue;
                SkelEdge edge1=edges.get(e1),edge2=edges.get(e2); int a=(edge1.from()==xi)?edge1.to():edge1.from(); int b=(edge2.from()==xi)?edge2.to():edge2.from(); if(a==b)continue;
                List<int[]> merged=new ArrayList<>(); boolean e1FX=(edge1.from()==xi); if(e1FX){for(int i=edge1.path().size()-1;i>=0;i--)merged.add(edge1.path().get(i));}else merged.addAll(edge1.path());
                boolean e2TX=(edge2.to()==xi); if(e2TX){for(int i=edge2.path().size()-1;i>=0;i--)merged.add(edge2.path().get(i));}else merged.addAll(edge2.path());
                deadN.add(xi);deadE.add(e1);deadE.add(e2); edges.add(new SkelEdge(a,b,merged)); int newEi=edges.size()-1; adj.get(a).add(newEi);adj.get(b).add(newEi); changed=true;
            }
        }
        int[] o2n=new int[n];Arrays.fill(o2n,-1);List<SkelNode> alive=new ArrayList<>();
        for(int i=0;i<n;i++)if(!deadN.contains(i)){o2n[i]=alive.size();SkelNode o=nodes.get(i);alive.add(new SkelNode(o2n[i],o.x(),o.y(),o.degree(),o.isEndpoint()));}
        List<SkelEdge> aliveE=new ArrayList<>();
        for(int ei=0;ei<edges.size();ei++)if(!deadE.contains(ei)){SkelEdge e=edges.get(ei);int nf=o2n[e.from()],nt=o2n[e.to()];if(nf>=0&&nt>=0)aliveE.add(new SkelEdge(nf,nt,e.path()));}
        nodes.clear();nodes.addAll(alive);edges.clear();edges.addAll(aliveE);
    }

    // ═══════════ Dominant-point edge splitting ═══════════

    /**
     * Splits every edge at its support points (max-deviation path pixels).
     *
     * <p>For each edge AB, a coordinate frame is built with origin at the
     * midpoint of the straight line AB, u-axis = AB direction, v-axis = ⊥.
     * For each of the four half-axes (u>0, u<0, v>0, v<0), the path pixel
     * with the greatest signed distance is selected as a new split node.
     * Points within 3px of an existing endpoint are skipped (u-axis extrema
     * naturally fall near A or B).
     *
     * <p>A split node is degree-2 (path-continuation point, not a junction
     * or endpoint). Its edges replace the original edge.
     */
    public static void splitEdgesAtSupportPoints(List<SkelNode> nodes, List<SkelEdge> edges,
            int imgW, int imgH) {
        int nextId = nodes.size();
        List<SkelNode> newNodes = new ArrayList<>(nodes);
        List<SkelEdge> newEdges = new ArrayList<>();

        // Guards
        int minEndDist = Math.max(imgW, imgH) / 5;
        java.util.HashSet<Long> existingPos = new java.util.HashSet<>();
        for (SkelNode nd : newNodes)
            existingPos.add(((long) nd.x() << 32) | (nd.y() & 0xFFFF_FFFFL));

        for (SkelEdge e : edges) {
            SkelNode a = newNodes.get(e.from()), b = newNodes.get(e.to());
            List<int[]> path = e.path();
            if (path.size() < 4) { newEdges.add(e); continue; } // too short

            // Build coordinate frame
            double mx = (a.x() + b.x()) / 2.0, my = (a.y() + b.y()) / 2.0;
            double dx = b.x() - a.x(), dy = b.y() - a.y();
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 4) { newEdges.add(e); continue; }
            double ux = dx / len, uy = dy / len;   // along-axis unit
            double vx = -uy, vy = ux;              // across-axis unit (⊥ CCW)

            // Scan path for extrema in each half-plane
            int bestPosU = -1, bestNegU = -1, bestPosV = -1, bestNegV = -1;
            double maxPosU = -1, maxNegU = -1, maxPosV = -1, maxNegV = -1;

            for (int i = 0; i < path.size(); i++) {
                int[] p = path.get(i);
                double wx = p[0] - mx, wy = p[1] - my;
                double u = wx * ux + wy * uy;
                double v = wx * vx + wy * vy;

                if (u > 0 && u > maxPosU) { maxPosU = u; bestPosU = i; }
                if (u < 0 && -u > maxNegU) { maxNegU = -u; bestNegU = i; }
                if (v > 0 && v > maxPosV) { maxPosV = v; bestPosV = i; }
                if (v < 0 && -v > maxNegV) { maxNegV = -v; bestNegV = i; }
            }

            // Collect unique split indices, sorted by path position
            java.util.BitSet splitIdxs = new java.util.BitSet(path.size());
            // U-axis extrema → only keep if far from endpoints (not just the existing nodes)
            if (bestPosU >= 0 && bestPosU > 2 && bestPosU < path.size() - 3) splitIdxs.set(bestPosU);
            if (bestNegU >= 0 && bestNegU > 2 && bestNegU < path.size() - 3) splitIdxs.set(bestNegU);
            // V-axis extrema → always keep (these are the true support points)
            if (bestPosV >= 0 && bestPosV > 1 && bestPosV < path.size() - 2) splitIdxs.set(bestPosV);
            if (bestNegV >= 0 && bestNegV > 1 && bestNegV < path.size() - 2) splitIdxs.set(bestNegV);

            if (splitIdxs.isEmpty()) { newEdges.add(e); continue; }

            // Split the edge: walk the path, cutting at each split index
            int prevId = e.from();
            List<int[]> segment = new ArrayList<>();
            for (int i = 0; i < path.size(); i++) {
                segment.add(path.get(i));
                if (splitIdxs.get(i)) {
                    int[] sp = path.get(i);

                    // Guard 1: angle SP→A vs SP→B (macro-angle to edge endpoints)
                    double dxA = a.x() - sp[0], dyA = a.y() - sp[1];
                    double dxB = b.x() - sp[0], dyB = b.y() - sp[1];
                    double lenA = Math.sqrt(dxA*dxA + dyA*dyA);
                    double lenB = Math.sqrt(dxB*dxB + dyB*dyB);
                    boolean sharp = true;
                    if (lenA > 1e-6 && lenB > 1e-6) {
                        double cos = (dxA*dxB + dyA*dyB) / (lenA * lenB);
                        cos = Math.max(-1, Math.min(1, cos));
                        double angle = Math.toDegrees(Math.acos(cos));
                        sharp = angle < 160 || angle > 200;
                    }

                    // Guard 2: not too close to any existing node
                    boolean farEnough = true;
                    for (SkelNode nd : newNodes) {
                        int ndx = sp[0] - nd.x(), ndy = sp[1] - nd.y();
                        if (ndx*ndx + ndy*ndy < minEndDist * minEndDist) {
                            farEnough = false; break;
                        }
                    }

                    // Guard 3: position not already occupied
                    long posKey = ((long) sp[0] << 32) | (sp[1] & 0xFFFF_FFFFL);
                    boolean novel = existingPos.add(posKey);

                    if (sharp && farEnough && novel) {
                        int splitId = nextId++;
                        newNodes.add(new SkelNode(splitId, sp[0], sp[1], 2, false));
                        newEdges.add(new SkelEdge(prevId, splitId, segment));
                        prevId = splitId;
                        segment = new ArrayList<>();
                    }
                }
            }
            // Final segment
            if (!segment.isEmpty())
                newEdges.add(new SkelEdge(prevId, e.to(), segment));
        }

        nodes.clear(); nodes.addAll(newNodes);
        edges.clear(); edges.addAll(newEdges);
    }

    private static List<List<Integer>> buildAdj(int n, List<SkelEdge> edges) { List<List<Integer>> adj=new ArrayList<>(n); for(int i=0;i<n;i++)adj.add(new ArrayList<>()); for(int ei=0;ei<edges.size();ei++){SkelEdge e=edges.get(ei);adj.get(e.from()).add(ei);adj.get(e.to()).add(ei);} return adj; }
}
