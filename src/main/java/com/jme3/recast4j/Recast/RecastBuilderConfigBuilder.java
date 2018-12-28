/*
 *  MIT License
 *  Copyright (c) 2018 MeFisto94
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */
package com.jme3.recast4j.Recast;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import org.recast4j.recast.RecastBuilder;
import org.recast4j.recast.RecastBuilderConfig;
import org.recast4j.recast.RecastConfig;

public class RecastBuilderConfigBuilder {
    protected Vector3f minBounds;
    protected Vector3f maxBounds;
    protected int tileX;
    protected int tileY;
    protected boolean useTiles;
    protected boolean buildDetailMesh;

    private RecastBuilderConfigBuilder() {
        tileX = 0;
        tileY = 0;
        useTiles = false;
        buildDetailMesh = true;
    }

    public RecastBuilderConfigBuilder(Vector3f minBounds, Vector3f maxBounds) {
        this();
        this.minBounds = minBounds;
        this.maxBounds = maxBounds;
    }

    private void fromBoundingVolume(BoundingVolume bv) {
        if (bv.getType() != BoundingVolume.Type.AABB) {
            throw new IllegalArgumentException("Requires a Mesh with an AABB Bounding Volume");
        }

        BoundingBox bb = (BoundingBox)bv;
        minBounds = bb.getMin(null);
        maxBounds = bb.getMax(null);
    }

    public RecastBuilderConfigBuilder(Mesh m) {
        this();
        fromBoundingVolume(m.getBound());
    }

    public RecastBuilderConfigBuilder(Node n) {
        this();
        fromBoundingVolume(n.getWorldBound());
    }

    public RecastBuilderConfigBuilder withTileX(int tileX) {
        useTiles = true;
        this.tileX = tileX;
        return this;
    }

    public RecastBuilderConfigBuilder withTileY(int tileY) {
        useTiles = true;
        this.tileY = tileY;
        return this;
    }

    public RecastBuilderConfigBuilder withDetailMesh(boolean buildDetailMesh) {
        this.buildDetailMesh = buildDetailMesh;
        return this;
    }

    public RecastBuilderConfig build(RecastConfig cfg) {
        return new RecastBuilderConfig(cfg, minBounds.toArray(null), maxBounds.toArray(null), tileX, tileY,
                useTiles, buildDetailMesh);
    }
}