package com.github.lumin.gui.clickgui.panel;

import com.github.lumin.graphics.shaders.BlurShader;
import com.github.lumin.gui.IComponent;
import com.github.lumin.gui.clickgui.component.impl.ColorSettingComponent;
import com.github.lumin.managers.ModuleManager;
import com.github.lumin.modules.Category;
import com.github.lumin.modules.Module;
import com.github.lumin.modules.impl.client.ClickGui;
import com.github.lumin.utils.render.MouseUtils;
import com.github.lumin.utils.render.animation.Animation;
import com.github.lumin.utils.render.animation.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ContentPanel implements IComponent {
    private final Minecraft mc = Minecraft.getInstance();

    private float x;
    private float y;
    private float width;
    private float height;
    private Category currentCategory;

    private final Animation viewAnimation = new Animation(Easing.EASE_OUT_EXPO, 450L);
    private final ListViewController listViewController = new ListViewController();
    private final SettingsViewController settingsViewController = new SettingsViewController();
    private final HudEditorViewController hudEditorViewController = new HudEditorViewController();

    private float sourceCardX;
    private float sourceCardY;
    private float sourceCardW;
    private float sourceCardH;
    private boolean closeSettingsRequested;
    private boolean exitAnimationStarted;
    private ViewState currentState = ViewState.LIST;
    private ViewState targetState = ViewState.LIST;

    private enum ViewState {
        LIST,
        SETTINGS,
        HUD_EDITOR,
        OPENING_SETTINGS,
        CLOSING_SETTINGS
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setCurrentCategory(Category category) {
        if (this.currentCategory == category) return;
        this.currentCategory = category;
        targetState = ViewState.LIST;
        closeSettingsRequested = false;
        settingsViewController.clearModule();
        currentState = ViewState.LIST;
        viewAnimation.setStartValue(0.0f);
        List<Module> modules = new ArrayList<>();
        for (Module module : ModuleManager.INSTANCE.getModules()) {
            if (module.category == category) {
                modules.add(module);
            }
        }
        listViewController.setModules(modules);
    }

    public void openHudEditor() {
        hudEditorViewController.refreshElements();
        targetState = ViewState.HUD_EDITOR;
    }

    @Override
    public void render(RendererSet set, int mouseX, int mouseY, float deltaTicks) {
        render(set, mouseX, mouseY, deltaTicks, 1.0f);
    }

    @Override
    public void render(RendererSet set, int mouseX, int mouseY, float deltaTicks, float alpha) {
        float guiScale = getGuiScale();
        float radius = guiScale * 20f;
        if (ClickGui.INSTANCE.isSidebarBlur()) {
            BlurShader.INSTANCE.drawBlur(x, y, width * guiScale, height * guiScale, 0, radius, radius, 0, ClickGui.INSTANCE.getBlurStrength());
        }
        set.bottomRoundRect().addRoundRect(x, y, width * guiScale, height * guiScale, 0, radius, radius, 0, new Color(0, 0, 0, 25));

        if (targetState != ViewState.HUD_EDITOR) {
            targetState = settingsViewController.hasActiveModule() && !closeSettingsRequested ? ViewState.SETTINGS : ViewState.LIST;
        }

        if (currentState != targetState) {
            if (targetState == ViewState.SETTINGS) {
                currentState = ViewState.OPENING_SETTINGS;
                viewAnimation.setStartValue(0.0f);
            } else if (targetState == ViewState.HUD_EDITOR || (currentState == ViewState.SETTINGS && targetState == ViewState.LIST)) {
                currentState = ViewState.CLOSING_SETTINGS;
                viewAnimation.setStartValue(1.0f);
                exitAnimationStarted = false;
            } else {
                currentState = targetState;
            }
        }

        if (currentState == ViewState.OPENING_SETTINGS) {
            viewAnimation.run(1.0f);
            if (viewAnimation.getValue() >= 0.99f) currentState = ViewState.SETTINGS;
            beginPanelClip(set, guiScale);
            renderListView(set, mouseX, mouseY, deltaTicks, alpha);
            settingsViewController.render(set, mouseX, mouseY, deltaTicks, alpha, x, y, width, height, guiScale);
            endPanelClip(set);
            return;
        }

        if (currentState == ViewState.CLOSING_SETTINGS) {
            closeSettingsRequested = true;
            beginPanelClip(set, guiScale);
            renderListView(set, mouseX, mouseY, deltaTicks, alpha);
            if (settingsViewController.hasActiveModule()) {
                if (!exitAnimationStarted) {
                    settingsViewController.startExitAnimation(sourceCardX, sourceCardY, sourceCardW, sourceCardH);
                    exitAnimationStarted = true;
                }
                settingsViewController.render(set, mouseX, mouseY, deltaTicks, alpha, x, y, width, height, guiScale);
                if (settingsViewController.isAnimationFinished()) {
                    currentState = targetState;
                    settingsViewController.clearModule();
                    closeSettingsRequested = false;
                    exitAnimationStarted = false;
                }
            } else {
                currentState = targetState;
            }
            endPanelClip(set);
            return;
        }

        if (currentState == ViewState.LIST) {
            Module suppressModule = currentState == ViewState.LIST ? null : settingsViewController.getModule();
            listViewController.render(set, mouseX, mouseY, deltaTicks, alpha, x, y, width, height, guiScale, suppressModule);
        } else if (currentState == ViewState.SETTINGS || currentState == ViewState.OPENING_SETTINGS || currentState == ViewState.CLOSING_SETTINGS) {
            settingsViewController.render(set, mouseX, mouseY, deltaTicks, alpha, x, y, width, height, guiScale);
        } else if (currentState == ViewState.HUD_EDITOR) {
            hudEditorViewController.render(set, mouseX, mouseY, deltaTicks, alpha, x, y, width, height, guiScale);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        float guiScale = getGuiScale();
        if (isTransitioning()) return true;

        if (currentState == ViewState.SETTINGS && ColorSettingComponent.hasActivePicker()) {
            if (ColorSettingComponent.isMouseOutOfPicker((int) event.x(), (int) event.y())) {
                ColorSettingComponent.closeActivePicker();
                return true;
            }
            boolean handled = settingsViewController.mouseClicked(event, focused, x, y, width, height, guiScale);
            if (settingsViewController.consumeExitRequest()) closeSettingsRequested = true;
            return handled;
        }

        if (!MouseUtils.isHovering(x, y, width * guiScale, height * guiScale, event.x(), event.y())) {
            listViewController.clickOutside();
            settingsViewController.clickOutside();
            hudEditorViewController.clickOutside();
            return false;
        }

        if (currentState == ViewState.LIST) {
            boolean handled = listViewController.mouseClicked(event, focused, x, y, width, height, guiScale);
            ListViewController.OpenRequest openRequest = listViewController.consumeOpenRequest();
            if (openRequest == null) return handled;

            closeSettingsRequested = false;
            sourceCardX = openRequest.sourceX();
            sourceCardY = openRequest.sourceY();
            sourceCardW = openRequest.sourceW();
            sourceCardH = openRequest.sourceH();
            settingsViewController.openModule(openRequest.module(), sourceCardX, sourceCardY, sourceCardW, sourceCardH, x, y, width, height, guiScale);
            currentState = ViewState.OPENING_SETTINGS;
            viewAnimation.setStartValue(0.0f);
            return true;
        } else if (currentState == ViewState.SETTINGS) {
            boolean handled = settingsViewController.mouseClicked(event, focused, x, y, width, height, guiScale);
            if (settingsViewController.consumeExitRequest()) closeSettingsRequested = true;
            return handled;
        } else if (currentState == ViewState.HUD_EDITOR) {
            return hudEditorViewController.mouseClicked(event, focused, x, y, width, height, guiScale);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (isTransitioning()) return true;
        if (currentState == ViewState.LIST) {
            return listViewController.mouseReleased(event, x, y, width, height, getGuiScale());
        } else if (currentState == ViewState.SETTINGS) {
            return settingsViewController.mouseReleased(event, x, y, width, height, getGuiScale());
        } else if (currentState == ViewState.HUD_EDITOR) {
            return hudEditorViewController.mouseReleased(event, x, y, width, height, getGuiScale());
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isTransitioning()) return true;
        if (currentState == ViewState.SETTINGS) return settingsViewController.mouseScrolled(mouseX, mouseY, scrollY);
        if (currentState == ViewState.LIST) return listViewController.mouseScrolled(mouseX, mouseY, scrollY);
        else if (currentState == ViewState.HUD_EDITOR) {
            float guiScale = getGuiScale();
            return hudEditorViewController.mouseScrolled(mouseX, mouseY, scrollY, guiScale);
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isTransitioning()) return true;
        if (currentState == ViewState.SETTINGS) return settingsViewController.keyPressed(event);
        if (currentState == ViewState.LIST) return listViewController.keyPressed(event);
        if (currentState == ViewState.HUD_EDITOR) {
            return hudEditorViewController.keyPressed(event);
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (isTransitioning()) return true;
        if (currentState == ViewState.LIST) {
            return listViewController.charTyped(event);
        } else if (currentState == ViewState.SETTINGS) {
            return settingsViewController.charTyped(event);
        } else if (currentState == ViewState.HUD_EDITOR) {
            return hudEditorViewController.charTyped(event);
        }
        return false;
    }

    private void renderListView(RendererSet set, int mouseX, int mouseY, float deltaTicks, float alpha) {
        Module suppressModule = currentState == ViewState.LIST ? null : settingsViewController.getModule();
        listViewController.render(set, mouseX, mouseY, deltaTicks, alpha, x, y, width, height, getGuiScale(), suppressModule);
    }

    public boolean isHudEditorActive() {
        return currentState == ViewState.HUD_EDITOR;
    }

    private boolean isTransitioning() {
        return currentState == ViewState.OPENING_SETTINGS || currentState == ViewState.CLOSING_SETTINGS;
    }

    private float getGuiScale() {
        return ClickGui.INSTANCE.scale.getValue().floatValue();
    }

    private void beginPanelClip(RendererSet set, float guiScale) {
        float clipX = x;
        float clipY = y;
        float clipW = width * guiScale;
        float clipH = height * guiScale;
        int fbW = mc.getWindow().getWidth();
        int fbH = mc.getWindow().getHeight();
        float pxScale = (float) mc.getWindow().getGuiScale();
        int scX = Mth.clamp(Mth.floor(clipX * pxScale), 0, fbW);
        int scY = Mth.clamp(Mth.floor((mc.getWindow().getGuiScaledHeight() - (clipY + clipH)) * pxScale), 0, fbH);
        int scW = Mth.clamp(Mth.ceil(clipW * pxScale), 0, fbW - scX);
        int scH = Mth.clamp(Mth.ceil(clipH * pxScale), 0, fbH - scY);
        set.bottomRoundRect().setScissor(scX, scY, scW, scH);
        set.topRoundRect().setScissor(scX, scY, scW, scH);
        set.font().setScissor(scX, scY, scW, scH);
    }

    private void endPanelClip(RendererSet set) {
        set.bottomRoundRect().clearScissor();
        set.topRoundRect().clearScissor();
        set.font().clearScissor();
    }
}
