package com.github.lumin.gui.clickgui.panel;

import com.github.lumin.graphics.renderers.*;
import com.github.lumin.graphics.shaders.BlurShader;
import com.github.lumin.gui.IComponent;
import com.github.lumin.modules.impl.client.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;

public class Panel implements IComponent {

    private final Minecraft mc = Minecraft.getInstance();

    private final RoundRectRenderer bottomRoundRect = new RoundRectRenderer();
    private final RoundRectRenderer topRoundRect = new RoundRectRenderer();
    private final RectRenderer rectRenderer = new RectRenderer();
    private final TextureRenderer textureRenderer = new TextureRenderer();
    private final TextRenderer fontRenderer = new TextRenderer();
    private final ShadowRenderer shadowRenderer = new ShadowRenderer();

    private final RendererSet set = new RendererSet(bottomRoundRect, topRoundRect, textureRenderer, fontRenderer, null, null, null, null);

    private final Sidebar sidebar = new Sidebar();
    private final ContentPanel contentPanel = new ContentPanel();


    public Panel() {
        sidebar.setOnSelect(contentPanel::setCurrentCategory);
        sidebar.setOnHudEditorSelect(contentPanel::openHudEditor);
        contentPanel.setCurrentCategory(sidebar.getSelectedCategory());
    }

    public void render(RendererSet set, int mouseX, int mouseY, float deltaTicks, float alpha) {
        float guiScale = ClickGui.INSTANCE.scale.getValue().floatValue();
        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();

        if (ClickGui.INSTANCE.backgroundBlackColor.getValue()) {
            rectRenderer.addRect(0, 0, screenWidth, screenHeight, new Color(18, 18, 18, (int) (110 * alpha)));
            rectRenderer.drawAndClear();
        }

        if (ClickGui.INSTANCE.isFullScreenBlur()) {
            BlurShader.INSTANCE.drawBlur(0.0f, 0.0f, screenWidth, screenHeight, 0.0f, ClickGui.INSTANCE.getBlurStrength());
        }

        PanelLayout layout = PanelLayout.compute(screenWidth, screenHeight, guiScale);
        float renderX = layout.x - (layout.width * guiScale - layout.width) / 2.0f;
        float renderY = layout.y - (layout.height * guiScale - layout.height) / 2.0f;
        float renderWidth = layout.width * guiScale;
        float renderHeight = layout.height * guiScale;
        float renderSidebarWidth = layout.sidebarWidth * guiScale;

        shadowRenderer.addShadow(renderX, renderY, renderWidth, renderHeight, 20f * guiScale, 12f * guiScale, ClickGui.INSTANCE.shadowColor.getValue());
        shadowRenderer.drawAndClear();

        sidebar.setBounds(renderX, renderY, layout.sidebarWidth, layout.height);
        contentPanel.setBounds(renderX + renderSidebarWidth, renderY, layout.contentWidth, layout.height);
        sidebar.render(this.set, mouseX, mouseY, deltaTicks, alpha);
        contentPanel.render(this.set, mouseX, mouseY, deltaTicks, alpha);

        bottomRoundRect.drawAndClear();
        topRoundRect.drawAndClear();
        textureRenderer.drawAndClear();
        fontRenderer.drawAndClear();

    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        return sidebar.mouseClicked(event, focused) || contentPanel.mouseClicked(event, focused);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return sidebar.mouseReleased(event) || contentPanel.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return sidebar.keyPressed(event) || contentPanel.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return sidebar.charTyped(event) || contentPanel.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return sidebar.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || contentPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean isHudEditorActive() {
        return contentPanel.isHudEditorActive();
    }

    private record PanelLayout(float x, float y, float width, float height, float sidebarWidth, float contentWidth) {
        private static PanelLayout compute(float screenWidth, float screenHeight, float guiScale) {
            float targetWidth = screenWidth * 0.5f;
            float minWidth = 400f;
            float width = Math.max(targetWidth, minWidth);
            float height = width * 9.0f / 16.0f;

            if (height * guiScale > screenHeight * 0.9f) {
                height = (screenHeight * 0.9f) / guiScale;
                width = height * 16.0f / 9.0f;
            }

            float x = (screenWidth - width) / 2.0f;
            float y = (screenHeight - height) / 2.0f;
            float sidebarWidth = Math.max(120f, width / 4);
            float contentWidth = width - sidebarWidth;
            return new PanelLayout(x, y, width, height, sidebarWidth, contentWidth);
        }
    }

}
