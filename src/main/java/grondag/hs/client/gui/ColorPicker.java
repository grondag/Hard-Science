/*******************************************************************************
 * Copyright 2019 grondag
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package grondag.hs.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.lwjgl.opengl.GL21;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import grondag.fermion.color.Color;
import grondag.fermion.gui.ScreenRenderContext;
import grondag.fermion.gui.control.AbstractControl;

@Environment(EnvType.CLIENT)
public class ColorPicker extends AbstractControl<ColorPicker> {
	private int hue = DEFAULT_HCL & 0xFF;
	private int hcl = DEFAULT_HCL;
	private int rgb = DEFAULT_RGB;

	private float centerX;
	private float centerY;
	private float radiusInner;
	private float radiusOuter;
	private float radiusSqInnerClick;
	private float radiusSqOuterClick;
	private float halfGrid;

	private final int[][] mix = new int[SLICE_COUNT][SLICE_COUNT];

	public int getRgb() {
		return rgb;
	}

	public void setRgb(int rgb) {
		this.rgb = rgb;
		hcl = RGB_TO_HCL.get(rgb);
		changeHueIfDifferent(hcl & 0xFF);
	}

	public ColorPicker(ScreenRenderContext renderContext) {
		super(renderContext);
		setAspectRatio(1.0f);
		rebuildMix();

		// TODO: remove
		hclToRgb(0, 0, 100);
	}

	@Override
	protected void drawContent(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		RenderSystem.disableTexture();
		RenderSystem.enableBlend();
		RenderSystem.disableAlphaTest();
		RenderSystem.defaultBlendFunc();
		RenderSystem.shadeModel(GL21.GL_SMOOTH);

		final Tessellator tessellator = Tessellator.getInstance();
		final BufferBuilder vertexbuffer = tessellator.getBuffer();

		vertexbuffer.begin(GL21.GL_QUADS, VertexFormats.POSITION_COLOR);

		float arcStart = 0;
		float arcEnd;

		float nx = (float) Math.sin(arcStart);
		float ny = (float) Math.cos(arcStart);

		float x0 = centerX + nx * radiusInner;
		float y0 = centerY + ny * radiusInner;
		float x1 = centerX + nx * radiusOuter;
		float y1 = centerY + ny * radiusOuter;

		float x2, y2, x3, y3;

		int hIndex = 0;
		int c0 = HUE_COLORS[hIndex];
		int c1;


		for (int h = 0; h <= 360; h += ARC_DEGREES) {
			arcEnd = (h + ARC_DEGREES) * DEGREES_TO_RADIANS;
			nx = (float) Math.sin(arcStart);
			ny = (float) Math.cos(arcStart);

			x2 = centerX + nx * radiusOuter;
			y2 = centerY + ny * radiusOuter;
			x3 = centerX + nx * radiusInner;
			y3 = centerY + ny * radiusInner;

			c1 = HUE_COLORS[++hIndex];

			vertexbuffer.vertex(x0, y0, 0.0D).color((c0 >> 16) & 0xFF, (c0 >> 8) & 0xFF, c0 & 0xFF, 0xFF).next();
			vertexbuffer.vertex(x1, y1, 0.0D).color((c0 >> 16) & 0xFF, (c0 >> 8) & 0xFF, c0 & 0xFF, 0xFF).next();
			vertexbuffer.vertex(x2, y2, 0.0D).color((c1 >> 16) & 0xFF, (c1 >> 8) & 0xFF, c1 & 0xFF, 0xFF).next();
			vertexbuffer.vertex(x3, y3, 0.0D).color((c1 >> 16) & 0xFF, (c1 >> 8) & 0xFF, c1 & 0xFF, 0xFF).next();

			arcStart = arcEnd;
			x0 = x3;
			y0 = y3;
			x1 = x2;
			y1 = y2;
			c0 = c1;
		}

		final float gLeft = centerX - halfGrid;
		final float gTop = centerY + halfGrid;
		final float gSpan = halfGrid * 2 / SLICE_COUNT;

		for (int lum = SLICE_COUNT - 1; lum >= 0; --lum) {
			for (int chr = 0; chr <  SLICE_COUNT; ++chr) {

				final int c = mix[lum][chr];


				if (c == NO_COLOR) {
					continue;
				}

				final int r = (c >> 16) & 0xFF;
				final int g = (c >> 8) & 0xFF;
				final int b = c & 0xFF;

				final float cx0 = gLeft + gSpan * chr;
				final float cx1 = cx0 + gSpan;

				final float ly0 = gTop - gSpan * lum;
				final float ly1 = ly0 - gSpan;

				vertexbuffer.vertex(cx0, ly0, 0.0D).color(r, g, b, 0xFF).next();
				vertexbuffer.vertex(cx1, ly0, 0.0D).color(r, g, b, 0xFF).next();
				vertexbuffer.vertex(cx1, ly1, 0.0D).color(r, g, b, 0xFF).next();
				vertexbuffer.vertex(cx0, ly1, 0.0D).color(r, g, b, 0xFF).next();
			}
		}

		tessellator.draw();
		RenderSystem.color4f(1, 1, 1, 1);
		RenderSystem.shadeModel(GL21.GL_FLAT);
		RenderSystem.disableBlend();
		RenderSystem.enableAlphaTest();
		RenderSystem.enableTexture();
	}

	private void changeHueIfDifferent(int newHue) {
		if (newHue != hue) {
			hue = newHue;
			rebuildMix();
		}
	}

	private void rebuildMix() {
		for (int lum = 0; lum < SLICE_COUNT; ++lum) {
			final int hl = hue | ((lum * SLICE_SIZE) << 16);

			for (int chr = 0; chr < SLICE_COUNT; ++chr) {
				mix[lum][chr] = HCL_TO_RGB.get(hl | ((chr * SLICE_SIZE) << 8));
			}
		}
	}

	@Override
	public void handleMouseClick(double mouseX, double mouseY, int clickedMouseButton) {
		final float dx = (float) (mouseX - centerX);
		final float dy = (float) (mouseY - centerY);

		final float distanceSq = dx * dx +  dy * dy;

		if (distanceSq < radiusSqOuterClick) {
			if (distanceSq > radiusSqInnerClick) {
				int angle = (int) Math.round(Math.toDegrees(Math.atan2(mouseX - centerX, mouseY - centerY)));

				if (angle < 0) {
					angle += 360;
				}

				changeHueIfDifferent(angle);
			} else {
				final float gLeft = centerX - halfGrid;
				final float gBottom = centerY - halfGrid;

				if  (mouseX >= gLeft  && mouseY >= gBottom) {
					final float gSpan = halfGrid * 2 / SLICE_COUNT;
					final int x = (int) Math.round((mouseX - gLeft) / gSpan);
					final int y = (int) Math.round((mouseY - gBottom) / gSpan);

					if (x < SLICE_COUNT && y < SLICE_COUNT) {
						final int hcl = hue  | ((x * SLICE_SIZE) << 8) | ((y * SLICE_SIZE) << 16);

						final int rgb = HCL_TO_RGB.getOrDefault(hcl, NO_COLOR);

						if (rgb != NO_COLOR) {
							this.rgb = rgb;
							this.hcl = hcl;
						}
					}
				}
			}

		}// else if (mouseX >= gridLeft) {
		//			final int l = (int) Math.floor((mouseY - gridTop) / gridIncrementY);
		//			final int c = (int) Math.floor((mouseX - gridLeft) / gridIncrementX);
		//
		//			if (l >= 0 && l < Luminance.COUNT && c >= 0 && c < Chroma.COUNT) {
		//				final ColorSet testMap = ColorAtlas.INSTANCE.getColorMap(selectedHue, Chroma.VALUES[c], Luminance.VALUES[l]);
		//
		//				if (testMap != null) {
		//					colorMapID = testMap.ordinal;
		//					selectedChroma = testMap.chroma;
		//				}
		//			}
		//		}
	}

	@Override
	protected void handleMouseDrag(double mouseX, double mouseY, int clickedMouseButton, double dx, double dy) {
		handleMouseClick(mouseX, mouseY, clickedMouseButton);
	}

	@Override
	protected void handleMouseScroll(double mouseX, double mouseY, double scrollDelta) {
		final int inc = mouseIncrementDelta();

		if (inc != 0) {
			int newHue = hue + inc;
			if (newHue < 0) {
				newHue += 360;
			} else if (newHue >= 360) {
				newHue -= 360;
			}

			changeHueIfDifferent(newHue);
		}
	}

	@Override
	protected void handleCoordinateUpdate() {
		radiusOuter = Math.min(height, width) / 2f;
		centerX = left + radiusOuter;
		centerY = top + radiusOuter;
		radiusInner = radiusOuter * 0.88f;
		radiusSqInnerClick = radiusInner * radiusInner * (0.95f * 0.95f);
		radiusSqOuterClick = radiusOuter * radiusOuter * (1.1f * 1.1f);
		halfGrid = 0.66f * radiusInner;
	}

	@Override
	public void drawToolTip(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		// TODO Auto-generated method stub

	}

	private static final double K = 903.3;
	private static final double E = 0.008856;

	//NB: using standard illuminant E - gamma and tone mapping to be applied in rendering
	private static final double ILLUMINANT = 100.0;

	private static final int NO_COLOR = 0;

	public static int labToRgb(double l, double a, double b) {
		final double y0 = (l + 16) / 116;
		final double z0 = y0 - b / 200;
		final double x0 = a / 500 + y0;

		final double x3 = x0 * x0 * x0;
		final double x1 = x3 > E ? x3 : (116 * x0 - 16) / K;

		final double y1 = l > K * E ? y0 * y0 * y0 : l / K;

		final double z3 = z0 * z0 * z0;

		final double z1 = z3 > E ? z3 : (116 * z0 - 16) / K;

		return xyzToRgb(x1 * ILLUMINANT, y1 * ILLUMINANT, z1 * ILLUMINANT);
	}

	public static int hclToRgb(double hue, double chroma, double luminance) {
		return labToRgb(luminance, Math.cos(Math.toRadians(hue)) * chroma, Math.sin(Math.toRadians(hue)) * chroma);
	}

	/**
	 * If color is visible, alpha btye = 255
	 */
	protected static int xyzToRgb(double x, double y, double z) {
		if (!(x >= 0 && x <= ILLUMINANT && y >= 0 && y <= ILLUMINANT && z >= 0 && z <= ILLUMINANT)) {
			return NO_COLOR;
		} else {
			// Convert to sRGB
			final double x0 = x / 100;
			final double y0 = y / 100;
			final double z0 = z / 100;

			final double r0 = x0 * 3.2406 + y0 * -1.5372 + z0 * -0.4986;
			final double g0 = x0 * -0.9689 + y0 * 1.8758 + z0 * 0.0415;
			final double b0 = x0 * 0.0557 + y0 * -0.2040 + z0 * 1.0570;

			final double r1 = (r0 > 0.0031308) ? (1.055 * Math.pow(r0, 1 / 2.4) - 0.055) : 12.92 * r0;
			final double g1 = (g0 > 0.0031308) ? (1.055 * Math.pow(g0, 1 / 2.4) - 0.055) : 12.92 * g0;
			final double b1 = (b0 > 0.0031308) ? (1.055 * Math.pow(b0, 1 / 2.4) - 0.055) : 12.92 * b0;

			if (r1 >= -0.000001 && r1 <= 1.000001 && g1 >= -0.000001 && g1 <= 1.000001 && b1 >= -0.000001
					&& b1 <= 1.000001) {
				final int red = (int) Math.round(r1 * 255);
				final int green = (int) Math.round(g1 * 255);
				final int blue = (int) Math.round(b1 * 255);
				return 0xFF000000 | (red << 16) | (green << 8) | blue;
			} else {
				return NO_COLOR;
			}
		}
	}

	private static final Int2IntOpenHashMap RGB_TO_HCL = new Int2IntOpenHashMap();
	private static final Int2IntOpenHashMap HCL_TO_RGB = new Int2IntOpenHashMap();

	private static final int SLICE_SIZE = 4;
	private static final int SLICE_COUNT = 26;

	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180F);
	private static final int ARC_DEGREES = 5;

	private static final int[] HUE_COLORS = new int[360 / ARC_DEGREES + 2];

	private static final int DEFAULT_RGB;
	private static final int DEFAULT_HCL = 100 << 16;

	static {
		for (int hIndex = 0; hIndex < HUE_COLORS.length; ++hIndex) {
			HUE_COLORS[hIndex] = Color.fromHCL(hIndex * ARC_DEGREES, Color.HCL_MAX, Color.HCL_MAX).ARGB | 0xFF000000;
		}

		for (int h = 0; h < 360; ++h) {
			for (int lum = 0; lum < SLICE_COUNT; ++lum) {
				for (int chr = 0; chr < SLICE_COUNT; ++chr) {
					final int c = chr * SLICE_SIZE;
					final int l = lum * SLICE_SIZE;

					final int rgb = hclToRgb(h, c, l);

					if (rgb != NO_COLOR) {
						final int hcl = h | (c << 8) | (l << 16);

						RGB_TO_HCL.put(rgb, hcl);
						HCL_TO_RGB.put(hcl, rgb);
					}
				}
			}
		}

		DEFAULT_RGB = HCL_TO_RGB.get(DEFAULT_HCL);
	}
}