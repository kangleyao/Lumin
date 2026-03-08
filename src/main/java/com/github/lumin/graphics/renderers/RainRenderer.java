package com.github.lumin.graphics.renderers;

import com.github.lumin.graphics.LuminRenderPipelines;
import com.github.lumin.graphics.LuminRenderSystem;
import com.github.lumin.graphics.buffer.LuminRingBuffer;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class RainRenderer implements IRenderer {
    private final Minecraft mc = Minecraft.getInstance();

    private static final int STRIDE = 24;
    private final long bufferSize;
    private final LuminRingBuffer buffer;
    private long currentOffset = 0;
    private int vertexCount = 0;
    private float time = 0f;

    private RenderTarget tempTarget;
    private GpuSampler linearSampler;
    private GpuBuffer quadBuffer;
    private int lastWidth = 0;
    private int lastHeight = 0;

    public RainRenderer() {
        this(32 * 1024);
    }

    public RainRenderer(long bufferSize) {
        this.bufferSize = bufferSize;
        this.buffer = new LuminRingBuffer(bufferSize, GpuBuffer.USAGE_VERTEX);
        this.linearSampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                1,
                OptionalDouble.empty()
        );
        initQuadBuffer();
    }

    private void initQuadBuffer() {
        ByteBuffer buf = MemoryUtil.memAlloc(80);
        buf.putFloat(-1f).putFloat(-1f).putFloat(0f).putFloat(0f).putFloat(0f);
        buf.putFloat(1f).putFloat(-1f).putFloat(0f).putFloat(1f).putFloat(0f);
        buf.putFloat(1f).putFloat(1f).putFloat(0f).putFloat(1f).putFloat(1f);
        buf.putFloat(-1f).putFloat(1f).putFloat(0f).putFloat(0f).putFloat(1f);
        buf.flip();
        quadBuffer = RenderSystem.getDevice().createBuffer(() -> "Rain Quad", GpuBuffer.USAGE_VERTEX, buf);
        MemoryUtil.memFree(buf);
    }

    public void setTime(float time) {
        this.time = time;
    }

    public float getTime() {
        return this.time;
    }

    public void update(float deltaTime) {
        this.time += deltaTime;
    }

    private void ensureTargetSize(int width, int height) {
        if (tempTarget == null || lastWidth != width || lastHeight != height) {
            if (tempTarget != null) {
                tempTarget.destroyBuffers();
            }
            tempTarget = new TextureTarget("Rain Temp", width, height, false, false);
            lastWidth = width;
            lastHeight = height;
        }
    }

    public void addRainEffect(float x, float y, float width, float height, Color color) {
        buffer.tryMap();

        if (currentOffset + (long) STRIDE * 4L > bufferSize) {
            return;
        }

        int argb = ARGB.toABGR(color.getRGB());

        float x2 = x + width;
        float y2 = y + height;

        long baseAddr = MemoryUtil.memAddress(buffer.getMappedBuffer());
        long p = baseAddr + currentOffset;

        writeVertex(p, x, y, 0f, 1f, argb);
        writeVertex(p + STRIDE, x, y2, 0f, 0f, argb);
        writeVertex(p + STRIDE * 2L, x2, y2, 1f, 0f, argb);
        writeVertex(p + STRIDE * 3L, x2, y, 1f, 1f, argb);

        currentOffset += (long) STRIDE * 4L;
        vertexCount += 4;
    }

    private void writeVertex(long addr, float x, float y, float u, float v, int color) {
        MemoryUtil.memPutFloat(addr, x);
        MemoryUtil.memPutFloat(addr + 4, y);
        MemoryUtil.memPutFloat(addr + 8, 0.0f);
        MemoryUtil.memPutFloat(addr + 12, u);
        MemoryUtil.memPutFloat(addr + 16, v);
        MemoryUtil.memPutInt(addr + 20, color);
    }

    @Override
    public void draw() {
        if (vertexCount == 0) return;

        var mainTarget = Minecraft.getInstance().getMainRenderTarget();
        if (mainTarget.getColorTextureView() == null) return;

        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        ensureTargetSize(width, height);

        copyToTempTarget(mainTarget);

        if (buffer.isMapped()) {
            buffer.unmap();
        }

        int indexCount = (vertexCount / 4) * 6;
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer ibo = autoIndices.getBuffer(indexCount);

        LuminRenderSystem.applyOrthoProjection();

        GpuBufferSlice dynamicUniforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1, 1, 1, 1),
                new Vector3f(0, 0, 0),
                TextureTransform.DEFAULT_TEXTURING.getMatrix()
        );

        ByteBuffer rainInfoBuf = MemoryUtil.memAlloc(16);
        rainInfoBuf.putFloat(time);
        rainInfoBuf.putFloat(0f);
        rainInfoBuf.putFloat(width);
        rainInfoBuf.putFloat(height);
        rainInfoBuf.flip();

        try (GpuBuffer rainInfoBuffer = RenderSystem.getDevice().createBuffer(() -> "Rain Info", GpuBuffer.USAGE_UNIFORM, rainInfoBuf)) {
            MemoryUtil.memFree(rainInfoBuf);

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Rain Effect Draw",
                    mainTarget.getColorTextureView(), OptionalInt.empty(),
                    null, OptionalDouble.empty())
            ) {
                pass.setPipeline(LuminRenderPipelines.RAIN);

                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicUniforms);
                pass.setUniform("RainInfo", rainInfoBuffer.slice());

                pass.setVertexBuffer(0, buffer.getGpuBuffer());
                pass.setIndexBuffer(ibo, autoIndices.type());

                pass.bindTexture("Sampler0", tempTarget.getColorTextureView(), linearSampler);

                pass.drawIndexed(0, 0, indexCount, 1);
            }
        }
    }

    private void copyToTempTarget(RenderTarget source) {
        RenderSystem.backupProjectionMatrix();

        ByteBuffer projBuf = MemoryUtil.memAlloc(64);
        for (int i = 0; i < 16; i++) {
            projBuf.putFloat(i % 5 == 0 ? 1.0f : 0.0f);
        }
        projBuf.flip();

        try (GpuBuffer projectionBuffer = RenderSystem.getDevice().createBuffer(() -> "Rain Projection", GpuBuffer.USAGE_UNIFORM, projBuf)) {
            MemoryUtil.memFree(projBuf);
            GpuBufferSlice projectionSlice = projectionBuffer.slice();
            RenderSystem.setProjectionMatrix(projectionSlice, ProjectionType.ORTHOGRAPHIC);

            ByteBuffer uniformBuf = MemoryUtil.memAlloc(32);
            uniformBuf.putFloat(0.5f / source.width).putFloat(0.5f / source.height);
            uniformBuf.putFloat(0f);
            uniformBuf.putFloat(0f);
            uniformBuf.putFloat(1f).putFloat(1f);
            uniformBuf.putFloat(0f).putFloat(0f);
            uniformBuf.flip();

            try (GpuBuffer uniformBuffer = RenderSystem.getDevice().createBuffer(() -> "Rain Copy Info", GpuBuffer.USAGE_UNIFORM, uniformBuf)) {
                MemoryUtil.memFree(uniformBuf);

                GpuBufferSlice dynamicUniforms = RenderSystem.getDynamicUniforms().writeTransform(
                        new Matrix4f(),
                        new Vector4f(1, 1, 1, 1),
                        new Vector3f(0, 0, 0),
                        new Matrix4f()
                );

                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Rain Copy",
                        tempTarget.getColorTextureView(), OptionalInt.of(0),
                        null, OptionalDouble.empty())
                ) {
                    pass.setPipeline(LuminRenderPipelines.BLUR_DOWN);

                    pass.setUniform("DynamicTransforms", dynamicUniforms);
                    pass.setUniform("BlurInfo", uniformBuffer.slice());
                    pass.setUniform("Projection", projectionSlice);

                    pass.setVertexBuffer(0, quadBuffer);

                    RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                    GpuBuffer ibo = autoIndices.getBuffer(6);
                    pass.setIndexBuffer(ibo, autoIndices.type());

                    pass.bindTexture("Sampler0", source.getColorTextureView(), linearSampler);

                    pass.drawIndexed(0, 0, 6, 1);
                }
            }
        }

        RenderSystem.restoreProjectionMatrix();
    }

    @Override
    public void clear() {
        if (vertexCount > 0) {
            if (buffer.isMapped()) {
                buffer.unmap();
            }
            buffer.rotate();
        }
        currentOffset = 0;
        vertexCount = 0;
    }

    @Override
    public void close() {
        clear();
        buffer.close();
        if (quadBuffer != null) {
            quadBuffer.close();
        }
        if (tempTarget != null) {
            tempTarget.destroyBuffers();
        }
        if (linearSampler != null) {
            linearSampler.close();
        }
    }
}
