package com.github.lumin.gui.clickgui.panel;

import com.github.lumin.assets.i18n.TranslateComponent;
import com.github.lumin.graphics.renderers.RoundRectRenderer;
import com.github.lumin.graphics.renderers.TextRenderer;
import com.github.lumin.gui.IComponent;
import com.github.lumin.gui.element.ElementManager;
import com.github.lumin.modules.Module;
import com.github.lumin.utils.render.MouseUtils;
import com.github.lumin.utils.render.animation.Animation;
import com.github.lumin.utils.render.animation.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class HudEditorViewController {
    private final Minecraft mc = Minecraft.getInstance();

    private final RoundRectRenderer listRoundRect = new RoundRectRenderer();
    private final TextRenderer listFont = new TextRenderer();
    private final List<HudElementCard> elementCards = new ArrayList<>();
    private final TranslateComponent searchComponent = TranslateComponent.create("gui", "search");

    private String searchText = "";
    private boolean searchFocused = false;
    private float scrollOffset = 0.0f;
    private float scrollTarget = 0.0f;
    private float maxScroll = 0.0f;
    private boolean draggingScrollbar = false;
    private float scrollbarDragStartMouseY = 0.0f;
    private float scrollbarDragStartScroll = 0.0f;

    private float lastSearchBoxX, lastSearchBoxY, lastSearchBoxW, lastSearchBoxH;
    private float lastListX, lastListY, lastListW, lastListH;
    private float lastScrollbarX, lastScrollbarY, lastScrollbarW, lastScrollbarH;
    private float lastThumbY, lastThumbH;

    public void refreshElements() {
        elementCards.clear();
        for (Module module : ElementManager.INSTANCE.getModulesWithElements()) {
            elementCards.add(new HudElementCard(module));
        }
        searchText = "";
        searchFocused = false;
        scrollOffset = 0.0f;
        scrollTarget = 0.0f;
        maxScroll = 0.0f;
        draggingScrollbar = false;
    }

    public void render(IComponent.RendererSet set, int mouseX, int mouseY, float deltaTicks, float alpha, float panelX, float panelY, float panelWidth, float panelHeight, float guiScale) {
        float padding = 8 * guiScale;
        float spacing = 4 * guiScale;
        float searchHeight = 24 * guiScale;
        float scaledPanelWidth = panelWidth * guiScale;

        lastSearchBoxX = panelX + padding;
        lastSearchBoxY = panelY + padding;
        lastSearchBoxW = scaledPanelWidth - padding * 2;
        lastSearchBoxH = searchHeight;
        renderSearchBox(set, lastSearchBoxX, lastSearchBoxY, lastSearchBoxW, searchHeight, guiScale, searchFocused, MouseUtils.isHovering(lastSearchBoxX, lastSearchBoxY, lastSearchBoxW, searchHeight, mouseX, mouseY), searchText, alpha);

        lastListX = panelX + padding;
        lastListY = lastSearchBoxY + searchHeight + padding;
        lastListW = Math.max(0.0f, scaledPanelWidth - padding * 2 - 4.0f * guiScale - 4.0f * guiScale);
        lastListH = Math.max(0.0f, (panelY + panelHeight * guiScale - padding) - lastListY);

        lastScrollbarX = lastListX + lastListW + 4.0f * guiScale;
        lastScrollbarY = lastListY;
        lastScrollbarW = 4.0f * guiScale;
        lastScrollbarH = lastListH;

        List<HudElementCard> visibleCards = new ArrayList<>();
        for (HudElementCard card : elementCards) {
            boolean matchesSearch = searchText.isEmpty() || card.module.getTranslatedName().toLowerCase().startsWith(searchText.toLowerCase());
            card.updateVisibility(matchesSearch);
            if (!matchesSearch && card.scaleAnimation.getValue() <= 0.01f) {
                card.width = 0;
                card.height = 0;
                continue;
            }
            visibleCards.add(card);
        }

        if (visibleCards.isEmpty() || lastListH <= 0.0f || lastListW <= 0.0f) {
            maxScroll = 0.0f;
            scrollOffset = 0.0f;
            scrollTarget = 0.0f;
            draggingScrollbar = false;
            lastThumbY = 0.0f;
            lastThumbH = 0.0f;
            return;
        }

        float itemGap = 8 * guiScale;
        int columns = 1; // HUD Editor usually list-style
        float cardWidth = lastListW;
        float cardHeight = 32.0f * guiScale;
        int totalRows = visibleCards.size();
        float contentH = totalRows * cardHeight + Math.max(0, totalRows - 1) * itemGap;

        maxScroll = Math.max(0.0f, contentH - lastListH);
        scrollTarget = Mth.clamp(scrollTarget, 0.0f, maxScroll);
        scrollOffset += (scrollTarget - scrollOffset) * 0.35f;
        scrollOffset = Mth.clamp(scrollOffset, 0.0f, maxScroll);

        lastThumbH = maxScroll <= 0.0f ? lastListH : Math.max(12.0f * guiScale, lastListH * (lastListH / contentH));
        float thumbTravel = Math.max(0.0f, lastListH - lastThumbH);
        lastThumbY = maxScroll <= 0.0f ? lastListY : lastListY + (scrollOffset / maxScroll) * thumbTravel;

        if (draggingScrollbar && maxScroll > 0.0f) {
            scrollTarget = handleScrollDrag(scrollTarget, maxScroll, lastThumbH, lastScrollbarH, mouseY, scrollbarDragStartMouseY, scrollbarDragStartScroll);
        }

        float pxScale = (float) mc.getWindow().getGuiScale();
        int scX = Mth.clamp(Mth.floor((lastListX - padding) * pxScale), 0, mc.getWindow().getWidth());
        int scY = Mth.clamp(Mth.floor((mc.getWindow().getGuiScaledHeight() - (lastListY + lastListH)) * pxScale), 0, mc.getWindow().getHeight());
        int scW = Mth.clamp(Mth.ceil((lastListW + padding * 2) * pxScale), 0, mc.getWindow().getWidth() - scX);
        int scH = Mth.clamp(Mth.ceil(lastListH * pxScale), 0, mc.getWindow().getHeight() - scY);
        listRoundRect.setScissor(scX, scY, scW, scH);
        listFont.setScissor(scX, scY, scW, scH);

        float listBottom = panelY + panelHeight * guiScale - padding;
        int visibleIndex = 0;
        for (HudElementCard card : visibleCards) {
            card.x = lastListX;
            card.y = lastListY + visibleIndex * (cardHeight + itemGap) - scrollOffset;
            card.width = cardWidth;
            card.height = cardHeight;
            if (card.shouldRender() && card.y + cardHeight >= lastListY && card.y <= listBottom) {
                card.render(listRoundRect, listFont, mouseX, mouseY, guiScale, alpha);
            }
            visibleIndex++;
        }

        listRoundRect.drawAndClear();
        listFont.drawAndClear();
        listRoundRect.clearScissor();
        listFont.clearScissor();

        if (maxScroll > 0.0f) {
            renderScrollbar(set, lastScrollbarX, lastListY, lastScrollbarW, lastListH, lastThumbY, lastThumbH, draggingScrollbar, MouseUtils.isHovering(lastScrollbarX, lastListY, lastScrollbarW, lastListH, mouseX, mouseY), MouseUtils.isHovering(lastScrollbarX, lastThumbY, lastScrollbarW, lastThumbH, mouseX, mouseY), alpha);
        }
    }

    private void renderSearchBox(IComponent.RendererSet set, float x, float y, float w, float h, float guiScale, boolean focused, boolean hovered, String text, float alpha) {
        Color bgColor = focused ? applyAlpha(new Color(50, 50, 50, 200), alpha) : (hovered ? applyAlpha(new Color(40, 40, 40, 200), alpha) : applyAlpha(new Color(30, 30, 30, 200), alpha));
        set.bottomRoundRect().addRoundRect(x, y, w, h, 8f * guiScale, bgColor);
        String display = text.isEmpty() && !focused ? searchComponent.getTranslatedName() : text;
        if (focused && (System.currentTimeMillis() % 1000 > 500)) display += "_";
        set.font().addText(display, x + 6 * guiScale, y + h / 2 - 7 * guiScale, guiScale * 0.9f, text.isEmpty() && !focused ? applyAlpha(Color.GRAY, alpha) : applyAlpha(Color.WHITE, alpha));
    }

    private static float handleScrollDrag(float scrollTarget, float maxScroll, float thumbH, float scrollbarH, float mouseY, float dragStartMouseY, float dragStartScroll) {
        float thumbTravel = Math.max(0.0f, scrollbarH - thumbH);
        if (thumbTravel <= 0.0f) return scrollTarget;
        float mouseDelta = mouseY - dragStartMouseY;
        return Mth.clamp(dragStartScroll + (mouseDelta / thumbTravel) * maxScroll, 0.0f, maxScroll);
    }

    private static void renderScrollbar(IComponent.RendererSet set, float x, float y, float w, float h, float thumbY, float thumbH, boolean dragging, boolean hovered, boolean thumbHovered, float alpha) {
        Color trackColor = hovered ? applyAlpha(new Color(255, 255, 255, 28), alpha) : applyAlpha(new Color(255, 255, 255, 18), alpha);
        Color thumbColor = dragging ? applyAlpha(new Color(255, 255, 255, 90), alpha) : (thumbHovered ? applyAlpha(new Color(255, 255, 255, 75), alpha) : applyAlpha(new Color(255, 255, 255, 55), alpha));
        set.bottomRoundRect().addRoundRect(x, y, w, h, w / 2.0f, trackColor);
        set.bottomRoundRect().addRoundRect(x, thumbY, w, thumbH, w / 2.0f, thumbColor);
    }

    private static Color applyAlpha(Color color, float alpha) {
        int a = Mth.clamp((int) (color.getAlpha() * alpha), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean focused, float panelX, float panelY, float panelWidth, float panelHeight, float guiScale) {
        float scaledWidth = panelWidth * guiScale;
        float scaledHeight = panelHeight * guiScale;
        if (!MouseUtils.isHovering(panelX, panelY, scaledWidth, scaledHeight, event.x(), event.y())) return false;

        if (MouseUtils.isHovering(lastSearchBoxX, lastSearchBoxY, lastSearchBoxW, lastSearchBoxH, event.x(), event.y())) {
            if (event.button() == 1) {
                searchText = "";
                scrollTarget = 0.0f;
            }
            searchFocused = true;
            return true;
        }

        searchFocused = false;

        if (event.button() == 0 && maxScroll > 0.0f && MouseUtils.isHovering(lastScrollbarX, lastScrollbarY, lastScrollbarW, lastScrollbarH, event.x(), event.y())) {
            if (MouseUtils.isHovering(lastScrollbarX, lastThumbY, lastScrollbarW, lastThumbH, event.x(), event.y())) {
                draggingScrollbar = true;
                scrollbarDragStartMouseY = (float) event.y();
                scrollbarDragStartScroll = scrollTarget;
                return true;
            }
            scrollTarget = handleScrollClick(scrollTarget, maxScroll, lastThumbH, lastThumbY, lastScrollbarX, lastScrollbarY, lastScrollbarW, lastScrollbarH, (float) event.x(), (float) event.y());
            draggingScrollbar = true;
            scrollbarDragStartMouseY = (float) event.y();
            scrollbarDragStartScroll = scrollTarget;
            return true;
        }

        if (event.button() == 0) {
            for (HudElementCard card : elementCards) {
                if (card.width > 0 && card.height > 0 && MouseUtils.isHovering(card.x, card.y, card.width, card.height, event.x(), event.y())) {
                    card.module.toggle();
                    return true;
                }
            }
        }

        return true;
    }

    private static float handleScrollClick(float scrollTarget, float maxScroll, float thumbH, float thumbY, float scrollbarX, float scrollbarY, float scrollbarW, float scrollbarH, float mouseX, float mouseY) {
        float thumbTravel = Math.max(0.0f, scrollbarH - thumbH);
        if (thumbTravel <= 0.0f) return scrollTarget;
        if (MouseUtils.isHovering(scrollbarX, thumbY, scrollbarW, thumbH, mouseX, mouseY)) return scrollTarget;
        float ratio = Mth.clamp((mouseY - scrollbarY - thumbH / 2.0f) / thumbTravel, 0.0f, 1.0f);
        return ratio * maxScroll;
    }

    public boolean mouseReleased(MouseButtonEvent event, float panelX, float panelY, float panelWidth, float panelHeight, float guiScale) {
        draggingScrollbar = false;
        return false;
    }

    public void clickOutside() {
        searchFocused = false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, float guiScale) {
        if (maxScroll <= 0.0f) return false;
        if (!MouseUtils.isHovering(lastListX, lastListY, lastListW + lastScrollbarW, lastListH, mouseX, mouseY)) return false;
        float step = 24.0f * guiScale;
        scrollTarget = Mth.clamp(scrollTarget - (float) scrollY * step, 0.0f, maxScroll);
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!searchFocused) return false;
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                scrollTarget = 0.0f;
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_ENTER) {
            searchFocused = false;
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!searchFocused) return false;
        searchText += Character.toString(event.codepoint());
        scrollTarget = 0.0f;
        return true;
    }

    private static final class HudElementCard {
        float x, y, width, height;
        final Module module;
        private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_QUAD, 120L);
        private final Animation enabledAnimation = new Animation(Easing.EASE_OUT_QUAD, 160L);
        private final Animation scaleAnimation = new Animation(Easing.EASE_OUT_EXPO, 350L);
        private boolean wasVisible = false;
        private boolean isAnimatingExit = false;

        private HudElementCard(Module module) {
            this.module = module;
            enabledAnimation.setStartValue(module.isEnabled() ? 1.0f : 0.0f);
            scaleAnimation.setStartValue(0.0f);
            scaleAnimation.run(1.0f);
        }

        private void updateVisibility(boolean visible) {
            if (visible && !wasVisible) {
                isAnimatingExit = false;
                scaleAnimation.setStartValue(0.0f);
                scaleAnimation.run(1.0f);
            } else if (!visible && wasVisible) {
                isAnimatingExit = true;
                scaleAnimation.setStartValue(1.0f);
                scaleAnimation.run(0.0f);
            }
            wasVisible = visible;
        }

        private boolean shouldRender() {
            return scaleAnimation.getValue() > 0.01f || !isAnimatingExit;
        }

        private void render(RoundRectRenderer round, TextRenderer text, int mouseX, int mouseY, float guiScale, float alpha) {
            if (width <= 0 || height <= 0) return;

            scaleAnimation.run(isAnimatingExit ? 0.0f : 1.0f);
            float scaleProgress = Mth.clamp(scaleAnimation.getValue(), 0.0f, 1.0f);
            if (scaleProgress <= 0.01f) return;

            boolean hovered = MouseUtils.isHovering(x, y, width, height, mouseX, mouseY);
            hoverAnimation.run(hovered ? 1.0f : 0.0f);
            enabledAnimation.run(module.isEnabled() ? 1.0f : 0.0f);
            float ht = Mth.clamp(hoverAnimation.getValue(), 0.0f, 1.0f);
            float et = Mth.clamp(enabledAnimation.getValue(), 0.0f, 1.0f);

            Color offColor = new Color(40, 40, 40, 130);
            Color onColor = new Color(0x35FFFFFF, true);
            int r = (int) (offColor.getRed() + (onColor.getRed() - offColor.getRed()) * et);
            int g = (int) (offColor.getGreen() + (onColor.getGreen() - offColor.getGreen()) * et);
            int b = (int) (offColor.getBlue() + (onColor.getBlue() - offColor.getBlue()) * et);
            int a = Mth.clamp((int) (offColor.getAlpha() + (onColor.getAlpha() - offColor.getAlpha()) * et) + (int) (24.0f * ht), 0, 255);

            int animAlpha = Mth.clamp((int) (a * alpha * scaleProgress), 0, 255);
            round.addRoundRect(x, y, width, height, 10f * guiScale, new Color(r, g, b, animAlpha));

            float nameScale = 1.0f * guiScale * scaleProgress;
            int textAlpha = Mth.clamp((int) (255 * alpha * scaleProgress), 0, 255);
            text.addText(module.getTranslatedName(), x + 10 * guiScale, y + (height - text.getHeight(nameScale)) / 2.0f, nameScale, new Color(255, 255, 255, textAlpha));

            // Status text
            String status = module.isEnabled() ? "ON" : "OFF";
            Color statusColor = module.isEnabled() ? new Color(0, 255, 0, textAlpha) : new Color(255, 0, 0, textAlpha);
            float statusScale = 0.8f * guiScale * scaleProgress;
            float statusW = text.getWidth(status, statusScale);
            text.addText(status, x + width - 10 * guiScale - statusW, y + (height - text.getHeight(statusScale)) / 2.0f, statusScale, statusColor);
        }
    }
}
