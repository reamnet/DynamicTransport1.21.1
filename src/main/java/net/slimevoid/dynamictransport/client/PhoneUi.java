package net.slimevoid.dynamictransport.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Shared rounded surfaces and controls for the elevator's compact, phone-like screens. */
final class PhoneUi {
  static final int BACKGROUND = 0xFFF2F2F7;
  static final int SURFACE = 0xFFFFFFFF;
  static final int TEXT = 0xFF1C1C1E;
  static final int SECONDARY = 0xFF73737D;
  static final int BLUE = 0xFF007AFF;
  static final int BLUE_TINT = 0xFFE4EFFF;
  static final int SEPARATOR = 0xFFE2E2E9;

  static void rounded(
      GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
    radius = Math.min(radius, Math.min(width, height) / 2);
    graphics.fill(x, y + radius, x + width, y + height - radius, color);
    for (int row = 0; row < radius; row++) {
      int inset =
          (int) Math.ceil(radius - Math.sqrt(radius * radius - Math.pow(radius - row - 0.5, 2)));
      graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
      graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
    }
  }

  static String fit(Font font, Component text, int width) {
    String value = text.getString();
    if (font.width(value) <= width) return value;
    return font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
  }

  static void heading(GuiGraphics graphics, Font font, Component text, int x, int y, int width) {
    float scale = Math.min(1.55F, (float) width / Math.max(1, font.width(text)));
    graphics.pose().pushPose();
    graphics.pose().translate(x, y, 0);
    graphics.pose().scale(scale, scale, 1);
    graphics.drawString(font, text, 0, 0, TEXT, false);
    graphics.pose().popPose();
  }

  static void centered(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
    graphics.drawString(font, text, x - font.width(text) / 2, y, color, false);
  }

  private static int blend(int start, int end, float amount) {
    int red = Math.round(Mth.lerp(amount, (start >> 16) & 255, (end >> 16) & 255));
    int green = Math.round(Mth.lerp(amount, (start >> 8) & 255, (end >> 8) & 255));
    int blue = Math.round(Mth.lerp(amount, start & 255, end & 255));
    return 0xFF000000 | red << 16 | green << 8 | blue;
  }

  enum Style {
    PRIMARY,
    PLAIN,
    CARD
  }

  static final class SoftButton extends Button {
    private final Style style;
    private final Component detail;
    private final boolean current;
    private float hover;

    SoftButton(int x, int y, int width, int height, Component label, Style style, OnPress press) {
      this(x, y, width, height, label, Component.empty(), style, false, press);
    }

    SoftButton(
        int x,
        int y,
        int width,
        int height,
        Component label,
        Component detail,
        Style style,
        boolean current,
        OnPress press) {
      super(x, y, width, height, label, press, DEFAULT_NARRATION);
      this.style = style;
      this.detail = detail;
      this.current = current;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      var font = Minecraft.getInstance().font;
      hover = Mth.lerp(0.25F, hover, isHoveredOrFocused() && active ? 1F : 0F);
      int fill =
          switch (style) {
            case PRIMARY -> active ? blend(BLUE, 0xFF0065D6, hover) : 0xFFB9CBE2;
            case CARD -> current ? BLUE_TINT : blend(SURFACE, BLUE_TINT, hover);
            case PLAIN -> blend(BACKGROUND, BLUE_TINT, hover);
          };
      rounded(
          graphics, getX(), getY(), getWidth(), getHeight(), style == Style.CARD ? 11 : 9, fill);
      if (isFocused()) {
        rounded(graphics, getX() + 3, getY() + getHeight() - 3, getWidth() - 6, 1, 0, BLUE);
      }
      if (style == Style.CARD) {
        graphics.drawString(
            font,
            fit(font, getMessage(), getWidth() - 47),
            getX() + 13,
            getY() + 9,
            current ? BLUE : TEXT,
            false);
        graphics.drawString(
            font, fit(font, detail, getWidth() - 47), getX() + 13, getY() + 24, SECONDARY, false);
        if (current) rounded(graphics, getX() + getWidth() - 24, getY() + 17, 7, 7, 3, BLUE);
        else graphics.drawString(font, "›", getX() + getWidth() - 20, getY() + 16, BLUE, false);
      } else {
        int color = style == Style.PRIMARY ? SURFACE : active ? BLUE : 0xFFB4B4BE;
        centered(
            graphics,
            font,
            Component.literal(fit(font, getMessage(), getWidth() - 8)),
            getX() + getWidth() / 2,
            getY() + (getHeight() - 9) / 2,
            color);
      }
    }

    @Override
    protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
      return Component.empty().append(getMessage()).append(". ").append(detail);
    }
  }
}
