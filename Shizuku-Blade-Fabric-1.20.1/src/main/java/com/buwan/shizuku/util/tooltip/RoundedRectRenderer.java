package com.buwan.shizuku.util.tooltip;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4i;

public class RoundedRectRenderer {
    public static void renderRoundedRect(
            GuiGraphics graphics,
            float x,
            float y,
            float width,
            float height,
            float topLeft,
            float topRight,
            float bottomRight,
            float bottomLeft,
            Vector4i color,
            int segments
    ) {
        renderRoundedRectGradient(
                graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, null, color, segments, RenderType.gui()
        );
    }

    public static void renderRoundedRectGradient(
            GuiGraphics graphics,
            float x,
            float y,
            float width,
            float height,
            float topLeft,
            float topRight,
            float bottomRight,
            float bottomLeft,
            GradientConfig gradient,
            Vector4i fallbackColor,
            int segments,
            RenderType renderType
    ) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(renderType);
        float maxRadius = Math.min(width, height) / 2.0F;
        topLeft = Math.min(topLeft, maxRadius);
        topRight = Math.min(topRight, maxRadius);
        bottomRight = Math.min(bottomRight, maxRadius);
        bottomLeft = Math.min(bottomLeft, maxRadius);
        float innerLeft = x + topLeft;
        float innerRight = x + width - topRight;
        float innerTop = y + topLeft;
        float innerBottom = y + height - bottomLeft;
        long time = System.currentTimeMillis();
        float nx = 0.0F;
        float ny = 0.0F;
        float nz = 1.0F;
        addQuadGradientWithConsumer(
                consumer, matrix, normal, innerLeft, innerTop, innerRight, innerBottom, x, y, width, height, gradient, fallbackColor, time, nx, ny, nz
        );
        addQuadGradientWithConsumer(
                consumer,
                matrix,
                normal,
                x + topLeft,
                y,
                x + width - topRight,
                y + Math.max(topLeft, topRight),
                x,
                y,
                width,
                height,
                gradient,
                fallbackColor,
                time,
                nx,
                ny,
                nz
        );
        addQuadGradientWithConsumer(
                consumer,
                matrix,
                normal,
                x + bottomLeft,
                y + height - Math.max(bottomLeft, bottomRight),
                x + width - bottomRight,
                y + height,
                x,
                y,
                width,
                height,
                gradient,
                fallbackColor,
                time,
                nx,
                ny,
                nz
        );
        addQuadGradientWithConsumer(
                consumer,
                matrix,
                normal,
                x,
                y + topLeft,
                x + Math.max(topLeft, bottomLeft),
                y + height - bottomLeft,
                x,
                y,
                width,
                height,
                gradient,
                fallbackColor,
                time,
                nx,
                ny,
                nz
        );
        addQuadGradientWithConsumer(
                consumer,
                matrix,
                normal,
                x + width - Math.max(topRight, bottomRight),
                y + topRight,
                x + width,
                y + height - bottomRight,
                x,
                y,
                width,
                height,
                gradient,
                fallbackColor,
                time,
                nx,
                ny,
                nz
        );
        if (topLeft > 0.0F) {
            addRoundedCornerGradientWithConsumer(
                    consumer,
                    matrix,
                    normal,
                    x + topLeft,
                    y + topLeft,
                    topLeft,
                    0.0F,
                    180.0F,
                    270.0F,
                    segments,
                    x,
                    y,
                    width,
                    height,
                    gradient,
                    fallbackColor,
                    time,
                    nx,
                    ny,
                    nz
            );
        }

        if (topRight > 0.0F) {
            addRoundedCornerGradientWithConsumer(
                    consumer,
                    matrix,
                    normal,
                    x + width - topRight,
                    y + topRight,
                    topRight,
                    0.0F,
                    270.0F,
                    360.0F,
                    segments,
                    x,
                    y,
                    width,
                    height,
                    gradient,
                    fallbackColor,
                    time,
                    nx,
                    ny,
                    nz
            );
        }

        if (bottomRight > 0.0F) {
            addRoundedCornerGradientWithConsumer(
                    consumer,
                    matrix,
                    normal,
                    x + width - bottomRight,
                    y + height - bottomRight,
                    bottomRight,
                    0.0F,
                    0.0F,
                    90.0F,
                    segments,
                    x,
                    y,
                    width,
                    height,
                    gradient,
                    fallbackColor,
                    time,
                    nx,
                    ny,
                    nz
            );
        }

        if (bottomLeft > 0.0F) {
            addRoundedCornerGradientWithConsumer(
                    consumer,
                    matrix,
                    normal,
                    x + bottomLeft,
                    y + height - bottomLeft,
                    bottomLeft,
                    0.0F,
                    90.0F,
                    180.0F,
                    segments,
                    x,
                    y,
                    width,
                    height,
                    gradient,
                    fallbackColor,
                    time,
                    nx,
                    ny,
                    nz
            );
        }

        graphics.flush();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void addQuadGradientWithConsumer(
            VertexConsumer consumer,
            Matrix4f matrix,
            Matrix3f normal,
            float x1,
            float y1,
            float x2,
            float y2,
            float rectX,
            float rectY,
            float rectWidth,
            float rectHeight,
            GradientConfig gradient,
            Vector4i fallbackColor,
            long time,
            float nx,
            float ny,
            float nz
    ) {
        int color1 = getColorAtPosition(x1, y1, rectX, rectY, rectWidth, rectHeight, gradient, fallbackColor, time);
        int color2 = getColorAtPosition(x1, y2, rectX, rectY, rectWidth, rectHeight, gradient, fallbackColor, time);
        int color3 = getColorAtPosition(x2, y2, rectX, rectY, rectWidth, rectHeight, gradient, fallbackColor, time);
        int color4 = getColorAtPosition(x2, y1, rectX, rectY, rectWidth, rectHeight, gradient, fallbackColor, time);
        addColoredVertexWithNormal(consumer, matrix, normal, x1, y1, 0.0F, color1, nx, ny, nz);
        addColoredVertexWithNormal(consumer, matrix, normal, x1, y2, 0.0F, color2, nx, ny, nz);
        addColoredVertexWithNormal(consumer, matrix, normal, x2, y2, 0.0F, color3, nx, ny, nz);
        addColoredVertexWithNormal(consumer, matrix, normal, x1, y1, 0.0F, color1, nx, ny, nz);
        addColoredVertexWithNormal(consumer, matrix, normal, x2, y2, 0.0F, color3, nx, ny, nz);
        addColoredVertexWithNormal(consumer, matrix, normal, x2, y1, 0.0F, color4, nx, ny, nz);
    }

    private static void addRoundedCornerGradientWithConsumer(
            VertexConsumer consumer,
            Matrix4f matrix,
            Matrix3f normal,
            float centerX,
            float centerY,
            float outerRadius,
            float innerRadius,
            float startAngle,
            float endAngle,
            int segments,
            float rectX,
            float rectY,
            float rectWidth,
            float rectHeight,
            GradientConfig gradient,
            Vector4i fallbackColor,
            long time,
            float nx,
            float ny,
            float nz
    ) {
        float angleStep = (endAngle - startAngle) / (float) segments;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) Math.toRadians(startAngle + angleStep * (float) i);
            float angle2 = (float) Math.toRadians(startAngle + angleStep * (float) (i + 1));
            float x1Outer = centerX + (float) Math.cos(angle1) * outerRadius;
            float y1Outer = centerY + (float) Math.sin(angle1) * outerRadius;
            float x2Outer = centerX + (float) Math.cos(angle2) * outerRadius;
            float y2Outer = centerY + (float) Math.sin(angle2) * outerRadius;
            float x1Inner = centerX + (float) Math.cos(angle1) * innerRadius;
            float y1Inner = centerY + (float) Math.sin(angle1) * innerRadius;
            float x2Inner = centerX + (float) Math.cos(angle2) * innerRadius;
            float y2Inner = centerY + (float) Math.sin(angle2) * innerRadius;
            int color1Outer = getColorAtPosition(x1Outer, y1Outer, rectX, rectY, rectWidth, rectHeight, gradient, fallbackColor, time);
            int color1Inner = getColorAtPosition(x1Inner, y1Inner, rectX, rectY, rectWidth, rectHeight, gradient, fallbackColor, time);
            int color2Inner = getColorAtPosition(x2Inner, y2Inner, rectX, rectY, rectWidth, rectHeight, gradient, fallbackColor, time);
            int color2Outer = getColorAtPosition(x2Outer, y2Outer, rectX, rectY, rectWidth, rectHeight, gradient, fallbackColor, time);
            addColoredVertexWithNormal(consumer, matrix, normal, x1Outer, y1Outer, 0.0F, color1Outer, nx, ny, nz);
            addColoredVertexWithNormal(consumer, matrix, normal, x1Inner, y1Inner, 0.0F, color1Inner, nx, ny, nz);
            addColoredVertexWithNormal(consumer, matrix, normal, x2Inner, y2Inner, 0.0F, color2Inner, nx, ny, nz);
            addColoredVertexWithNormal(consumer, matrix, normal, x1Outer, y1Outer, 0.0F, color1Outer, nx, ny, nz);
            addColoredVertexWithNormal(consumer, matrix, normal, x2Inner, y2Inner, 0.0F, color2Inner, nx, ny, nz);
            addColoredVertexWithNormal(consumer, matrix, normal, x2Outer, y2Outer, 0.0F, color2Outer, nx, ny, nz);
        }
    }

    private static void addColoredVertexWithNormal(
            VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, float x, float y, float z, int color, float nx, float ny, float nz
    ) {
        int a = color >> 24 & 0xFF;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        consumer.vertex(matrix, x, y, z).color(r, g, b, a).setNormal(nx, ny, nz);
    }

    public static void renderRoundedRectBorder(
            GuiGraphics graphics,
            float x,
            float y,
            float width,
            float height,
            float topLeft,
            float topRight,
            float bottomRight,
            float bottomLeft,
            float borderWidth,
            Vector4i color,
            int segments
    ) {
        renderRoundedRectBorderGradient(
                graphics, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, borderWidth, null, color, segments
        );
    }

    public static void renderRoundedRectBorderGradient(
            GuiGraphics graphics,
            float x,
            float y,
            float width,
            float height,
            float topLeft,
            float topRight,
            float bottomRight,
            float bottomLeft,
            float borderWidth,
            GradientConfig gradient,
            Vector4i fallbackColor,
            int segments
    ) {
        PoseStack poseStack = graphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = com.buwan.shizuku.compat.TesselatorCompat.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float maxRadius = Math.min(width, height) / 2.0F;
        topLeft = Math.min(topLeft, maxRadius);
        topRight = Math.min(topRight, maxRadius);
        bottomRight = Math.min(bottomRight, maxRadius);
        bottomLeft = Math.min(bottomLeft, maxRadius);
        long time = System.currentTimeMillis();
        float perimeter = calculatePerimeter(width, height, topLeft, topRight, bottomRight, bottomLeft);
        float topLength = width - topLeft - topRight;
        int topSegments = Math.max(2, (int) (topLength / 5.0F));
        float topStart = 0.0F;
        addTopBorderSegmented(
                builder, matrix, x + topLeft, y, x + width - topRight, y, borderWidth, topSegments, topStart, topLength, perimeter, gradient, fallbackColor, time
        );
        float rightLength = height - topRight - bottomRight;
        int rightSegments = Math.max(2, (int) (rightLength / 5.0F));
        float rightStart = (float) (topLength + Math.PI * topRight / 2.0);
        addRightBorderSegmented(
                builder,
                matrix,
                x + width,
                y + topRight,
                x + width,
                y + height - bottomRight,
                borderWidth,
                rightSegments,
                rightStart,
                rightLength,
                perimeter,
                gradient,
                fallbackColor,
                time
        );
        float bottomLength = width - bottomLeft - bottomRight;
        int bottomSegments = Math.max(2, (int) (bottomLength / 5.0F));
        float bottomStart = (float) (topLength + Math.PI * topRight / 2.0 + rightLength + Math.PI * bottomRight / 2.0);
        addBottomBorderSegmented(
                builder,
                matrix,
                x + width - bottomRight,
                y + height,
                x + bottomLeft,
                y + height,
                borderWidth,
                bottomSegments,
                bottomStart,
                bottomLength,
                perimeter,
                gradient,
                fallbackColor,
                time
        );
        float leftLength = height - topLeft - bottomLeft;
        int leftSegments = Math.max(2, (int) (leftLength / 5.0F));
        float leftStart = (float) (
                topLength
                        + Math.PI * topRight / 2.0
                        + rightLength
                        + Math.PI * bottomRight / 2.0
                        + bottomLength
                        + Math.PI * bottomLeft / 2.0
        );
        addLeftBorderSegmented(
                builder,
                matrix,
                x,
                y + height - bottomLeft,
                x,
                y + topLeft,
                borderWidth,
                leftSegments,
                leftStart,
                leftLength,
                perimeter,
                gradient,
                fallbackColor,
                time
        );
        if (topLeft > 0.0F) {
            addRoundedCornerBorderCircular(
                    builder,
                    matrix,
                    x + topLeft,
                    y + topLeft,
                    topLeft,
                    topLeft - borderWidth,
                    180.0F,
                    270.0F,
                    segments,
                    (float) (
                            topLength
                                    + Math.PI * topRight / 2.0
                                    + rightLength
                                    + Math.PI * bottomRight / 2.0
                                    + bottomLength
                                    + Math.PI * bottomLeft / 2.0
                                    + leftLength
                    ),
                    (float) (Math.PI * topLeft / 2.0),
                    perimeter,
                    gradient,
                    fallbackColor,
                    time
            );
        }

        if (topRight > 0.0F) {
            addRoundedCornerBorderCircular(
                    builder,
                    matrix,
                    x + width - topRight,
                    y + topRight,
                    topRight,
                    topRight - borderWidth,
                    270.0F,
                    360.0F,
                    segments,
                    topLength,
                    (float) (Math.PI * topRight / 2.0),
                    perimeter,
                    gradient,
                    fallbackColor,
                    time
            );
        }

        if (bottomRight > 0.0F) {
            addRoundedCornerBorderCircular(
                    builder,
                    matrix,
                    x + width - bottomRight,
                    y + height - bottomRight,
                    bottomRight,
                    bottomRight - borderWidth,
                    0.0F,
                    90.0F,
                    segments,
                    (float) (topLength + Math.PI * topRight / 2.0 + rightLength),
                    (float) (Math.PI * bottomRight / 2.0),
                    perimeter,
                    gradient,
                    fallbackColor,
                    time
            );
        }

        if (bottomLeft > 0.0F) {
            addRoundedCornerBorderCircular(
                    builder,
                    matrix,
                    x + bottomLeft,
                    y + height - bottomLeft,
                    bottomLeft,
                    bottomLeft - borderWidth,
                    90.0F,
                    180.0F,
                    segments,
                    (float) (topLength + Math.PI * topRight / 2.0 + rightLength + Math.PI * bottomRight / 2.0 + bottomLength),
                    (float) (Math.PI * bottomLeft / 2.0),
                    perimeter,
                    gradient,
                    fallbackColor,
                    time
            );
        }

        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
    }

    private static float calculatePerimeter(float width, float height, float topLeft, float topRight, float bottomRight, float bottomLeft) {
        float straightEdges = width
                - topLeft
                - topRight
                + (height - topRight - bottomRight)
                + (width - bottomRight - bottomLeft)
                + (height - bottomLeft - topLeft);
        float corners = (float) (Math.PI * (topLeft + topRight + bottomRight + bottomLeft) / 2.0);
        return straightEdges + corners;
    }

    private static void addTopBorderSegmented(
            BufferBuilder builder,
            Matrix4f matrix,
            float x1,
            float y1,
            float x2,
            float y2,
            float borderWidth,
            int segments,
            float startDistance,
            float edgeLength,
            float totalPerimeter,
            GradientConfig gradient,
            Vector4i fallbackColor,
            long time
    ) {
        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / (float) segments;
            float t2 = (float) (i + 1) / (float) segments;
            float px1 = x1 + (x2 - x1) * t1;
            float px2 = x1 + (x2 - x1) * t2;
            float dist1 = startDistance + edgeLength * t1;
            float dist2 = startDistance + edgeLength * t2;
            float progress1 = dist1 / totalPerimeter;
            float progress2 = dist2 / totalPerimeter;
            int color1 = getBorderColorAtProgress(progress1, gradient, fallbackColor, time);
            int color2 = getBorderColorAtProgress(progress2, gradient, fallbackColor, time);
            addColoredVertex(builder, matrix, px1, y1, color1);
            addColoredVertex(builder, matrix, px1, y1 + borderWidth, color1);
            addColoredVertex(builder, matrix, px2, y2 + borderWidth, color2);
            addColoredVertex(builder, matrix, px1, y1, color1);
            addColoredVertex(builder, matrix, px2, y2 + borderWidth, color2);
            addColoredVertex(builder, matrix, px2, y2, color2);
        }
    }

    private static void addRightBorderSegmented(
            BufferBuilder builder,
            Matrix4f matrix,
            float x1,
            float y1,
            float x2,
            float y2,
            float borderWidth,
            int segments,
            float startDistance,
            float edgeLength,
            float totalPerimeter,
            GradientConfig gradient,
            Vector4i fallbackColor,
            long time
    ) {
        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / (float) segments;
            float t2 = (float) (i + 1) / (float) segments;
            float py1 = y1 + (y2 - y1) * t1;
            float py2 = y1 + (y2 - y1) * t2;
            float dist1 = startDistance + edgeLength * t1;
            float dist2 = startDistance + edgeLength * t2;
            float progress1 = dist1 / totalPerimeter;
            float progress2 = dist2 / totalPerimeter;
            int color1 = getBorderColorAtProgress(progress1, gradient, fallbackColor, time);
            int color2 = getBorderColorAtProgress(progress2, gradient, fallbackColor, time);
            addColoredVertex(builder, matrix, x1, py1, color1);
            addColoredVertex(builder, matrix, x1 - borderWidth, py1, color1);
            addColoredVertex(builder, matrix, x2 - borderWidth, py2, color2);
            addColoredVertex(builder, matrix, x1, py1, color1);
            addColoredVertex(builder, matrix, x2 - borderWidth, py2, color2);
            addColoredVertex(builder, matrix, x2, py2, color2);
        }
    }

    private static void addBottomBorderSegmented(
            BufferBuilder builder,
            Matrix4f matrix,
            float x1,
            float y1,
            float x2,
            float y2,
            float borderWidth,
            int segments,
            float startDistance,
            float edgeLength,
            float totalPerimeter,
            GradientConfig gradient,
            Vector4i fallbackColor,
            long time
    ) {
        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / (float) segments;
            float t2 = (float) (i + 1) / (float) segments;
            float px1 = x1 + (x2 - x1) * t1;
            float px2 = x1 + (x2 - x1) * t2;
            float dist1 = startDistance + edgeLength * t1;
            float dist2 = startDistance + edgeLength * t2;
            float progress1 = dist1 / totalPerimeter;
            float progress2 = dist2 / totalPerimeter;
            int color1 = getBorderColorAtProgress(progress1, gradient, fallbackColor, time);
            int color2 = getBorderColorAtProgress(progress2, gradient, fallbackColor, time);
            addColoredVertex(builder, matrix, px1, y1, color1);
            addColoredVertex(builder, matrix, px1, y1 - borderWidth, color1);
            addColoredVertex(builder, matrix, px2, y2 - borderWidth, color2);
            addColoredVertex(builder, matrix, px1, y1, color1);
            addColoredVertex(builder, matrix, px2, y2 - borderWidth, color2);
            addColoredVertex(builder, matrix, px2, y2, color2);
        }
    }

    private static void addLeftBorderSegmented(
            BufferBuilder builder,
            Matrix4f matrix,
            float x1,
            float y1,
            float x2,
            float y2,
            float borderWidth,
            int segments,
            float startDistance,
            float edgeLength,
            float totalPerimeter,
            GradientConfig gradient,
            Vector4i fallbackColor,
            long time
    ) {
        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / (float) segments;
            float t2 = (float) (i + 1) / (float) segments;
            float py1 = y1 + (y2 - y1) * t1;
            float py2 = y1 + (y2 - y1) * t2;
            float dist1 = startDistance + edgeLength * t1;
            float dist2 = startDistance + edgeLength * t2;
            float progress1 = dist1 / totalPerimeter;
            float progress2 = dist2 / totalPerimeter;
            int color1 = getBorderColorAtProgress(progress1, gradient, fallbackColor, time);
            int color2 = getBorderColorAtProgress(progress2, gradient, fallbackColor, time);
            addColoredVertex(builder, matrix, x1, py1, color1);
            addColoredVertex(builder, matrix, x1 + borderWidth, py1, color1);
            addColoredVertex(builder, matrix, x2 + borderWidth, py2, color2);
            addColoredVertex(builder, matrix, x1, py1, color1);
            addColoredVertex(builder, matrix, x2 + borderWidth, py2, color2);
            addColoredVertex(builder, matrix, x2, py2, color2);
        }
    }

    private static void addRoundedCornerBorderCircular(
            BufferBuilder builder,
            Matrix4f matrix,
            float centerX,
            float centerY,
            float outerRadius,
            float innerRadius,
            float startAngle,
            float endAngle,
            int segments,
            float startDistance,
            float arcLength,
            float totalPerimeter,
            GradientConfig gradient,
            Vector4i fallbackColor,
            long time
    ) {
        float angleStep = (endAngle - startAngle) / (float) segments;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) Math.toRadians(startAngle + angleStep * (float) i);
            float angle2 = (float) Math.toRadians(startAngle + angleStep * (float) (i + 1));
            float x1Outer = centerX + (float) Math.cos(angle1) * outerRadius;
            float y1Outer = centerY + (float) Math.sin(angle1) * outerRadius;
            float x2Outer = centerX + (float) Math.cos(angle2) * outerRadius;
            float y2Outer = centerY + (float) Math.sin(angle2) * outerRadius;
            float x1Inner = centerX + (float) Math.cos(angle1) * innerRadius;
            float y1Inner = centerY + (float) Math.sin(angle1) * innerRadius;
            float x2Inner = centerX + (float) Math.cos(angle2) * innerRadius;
            float y2Inner = centerY + (float) Math.sin(angle2) * innerRadius;
            float t1 = (float) i / (float) segments;
            float t2 = (float) (i + 1) / (float) segments;
            float dist1 = startDistance + arcLength * t1;
            float dist2 = startDistance + arcLength * t2;
            float progress1 = dist1 / totalPerimeter;
            float progress2 = dist2 / totalPerimeter;
            int color1 = getBorderColorAtProgress(progress1, gradient, fallbackColor, time);
            int color2 = getBorderColorAtProgress(progress2, gradient, fallbackColor, time);
            addColoredVertex(builder, matrix, x1Outer, y1Outer, color1);
            addColoredVertex(builder, matrix, x1Inner, y1Inner, color1);
            addColoredVertex(builder, matrix, x2Inner, y2Inner, color2);
            addColoredVertex(builder, matrix, x1Outer, y1Outer, color1);
            addColoredVertex(builder, matrix, x2Inner, y2Inner, color2);
            addColoredVertex(builder, matrix, x2Outer, y2Outer, color2);
        }
    }

    private static int getColorAtPosition(
            float px, float py, float rectX, float rectY, float rectWidth, float rectHeight, GradientConfig gradient, Vector4i fallbackColor, long time
    ) {
        if (gradient == null) {
            return fallbackColor.w << 24 | fallbackColor.x << 16 | fallbackColor.y << 8 | fallbackColor.z;
        } else {
            float progress = 0.0F;
            switch (gradient.getType()) {
                case HORIZONTAL:
                case ANIMATED:
                    progress = (px - rectX) / rectWidth;
                    break;
                case VERTICAL:
                    progress = (py - rectY) / rectHeight;
                    break;
                case RADIAL:
                    float centerX = rectX + rectWidth / 2.0F;
                    float centerY = rectY + rectHeight / 2.0F;
                    float dx = px - centerX;
                    float dy = py - centerY;
                    float maxDist = (float) Math.sqrt(rectWidth * rectWidth + rectHeight * rectHeight) / 2.0F;
                    progress = (float) Math.sqrt(dx * dx + dy * dy) / maxDist;
                    break;
                default:
                    break;
            }

            progress = Math.max(0.0F, Math.min(1.0F, progress));
            return gradient.getColorAt(progress, time);
        }
    }

    private static int getBorderColorAtProgress(float progress, GradientConfig gradient, Vector4i fallbackColor, long time) {
        return gradient != null && gradient.getType() == GradientConfig.GradientType.BORDER_CIRCULAR
                ? gradient.getColorAt(progress, time)
                : fallbackColor.w << 24 | fallbackColor.x << 16 | fallbackColor.y << 8 | fallbackColor.z;
    }

    private static void addColoredVertex(BufferBuilder builder, Matrix4f matrix, float x, float y, int color) {
        int a = color >> 24 & 0xFF;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        builder.vertex(matrix, x, y, 0.0F).color(r, g, b, a);
    }
}
