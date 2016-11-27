/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.worldgen.generator.custom;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.biome.Biome;

import java.util.Random;

import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.util.CubePos;
import cubicchunks.world.ICubicWorld;
import cubicchunks.worldgen.generator.ICubePrimer;
import cubicchunks.worldgen.generator.custom.builder.BiomeHeightVolatilitySource;
import cubicchunks.worldgen.generator.custom.builder.IBuilder;
import cubicchunks.worldgen.generator.custom.builder.NoiseSource;
import mcp.MethodsReturnNonnullByDefault;

import static cubicchunks.worldgen.generator.GlobalGeneratorConfig.MAX_ELEV;
import static cubicchunks.worldgen.generator.GlobalGeneratorConfig.X_SECTION_SIZE;
import static cubicchunks.worldgen.generator.GlobalGeneratorConfig.Z_SECTION_SIZE;
import static cubicchunks.worldgen.generator.custom.builder.IBuilder.NEGATIVE;
import static cubicchunks.worldgen.generator.custom.builder.IBuilder.NOT_NEGATIVE;

/**
 * A terrain generator that supports infinite(*) worlds
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CustomTerrainGenerator {
	// Number of octaves for the noise function
	private static final int OCTAVES = 16;
	private final IBuilder terrainBuilder;
	private final BiomeHeightVolatilitySource biomeSource;

	public CustomTerrainGenerator(ICubicWorld world, final long seed) {
		final int selectorOctaves = 8;
		Random rnd = new Random(seed);
		IBuilder selector = NoiseSource.perlin().
			seed(rnd.nextLong()).
			frequency(8.55515/Math.pow(2, selectorOctaves)/(MAX_ELEV/64.0)).
			octaves(selectorOctaves).
			create();

		IBuilder low = NoiseSource.perlin().
			seed(rnd.nextLong()).
			frequency(684.412D/Math.pow(2, OCTAVES)/(MAX_ELEV/64.0)).
			octaves(OCTAVES).
			create();

		IBuilder high = NoiseSource.perlin().
			seed(rnd.nextLong()).
			frequency(684.412D/Math.pow(2, OCTAVES)/(MAX_ELEV/64.0)).
			octaves(OCTAVES).
			create();

		int heightmapOctaves = 10;
		double heightmapFreq = 200.0/Math.pow(2, heightmapOctaves)/(MAX_ELEV/64);
		IBuilder randomHeight2d = NoiseSource.perlin().
			seed(rnd.nextLong()).
			frequency(heightmapFreq, 0, heightmapFreq).
			octaves(heightmapOctaves).
			create().
			mulIf(NEGATIVE, -0.3).
			mul(3).sub(2).
			clamp(-2, 1).
			divIf(NEGATIVE, 2*2*1.4).
			divIf(NOT_NEGATIVE, 8).
			mul(0.2*17/64.0);

		this.biomeSource = new BiomeHeightVolatilitySource(
			world.getBiomeProvider(), 2*(int) (MAX_ELEV/64), X_SECTION_SIZE, Z_SECTION_SIZE);

		IBuilder height = biomeSource::getHeight;
		IBuilder volatility = biomeSource::getVolatility;

		this.terrainBuilder = selector.
			lerp(low, high).
			mul(volatility.div((x, y, z) -> (y*8/MAX_ELEV < height.get(x, y, z)) ? 4 : 1)).
			add(height).add(randomHeight2d).
			mul(MAX_ELEV).add(64).sub((x, y, z) -> y*8);
	}

	/**
	 * Generate the cube as the specified location
	 *
	 * @param cubePrimer cube primer to use
	 * @param cubeX cube x location
	 * @param cubeY cube y location
	 * @param cubeZ cube z location
	 */
	public void generate(final ICubePrimer cubePrimer, int cubeX, int cubeY, int cubeZ) {
		CubePos cubePos = new CubePos(cubeX, cubeY, cubeZ);
		BlockPos start = cubePos.getMinBlockPos();
		BlockPos end = cubePos.getMaxBlockPos();
		biomeSource.setChunk(cubeX, cubeZ);
		terrainBuilder.scaledStream(start, end, new Vec3i(4, 8, 4)).map(this::getBlock).forEach(b -> b.setBlock(cubePrimer));
	}

	/**
	 * Retrieve the blockstate appropriate for the specified builder entry
	 *
	 * @return The block state
	 */
	private BlockStateInstance getBlock(IBuilder.IExtendedEntry entry) {
		int x = entry.getX();
		int y = entry.getY();
		int z = entry.getZ();
		double density = entry.getValue();
		double yGrad = entry.getYGradient();
		Biome biome = biomeSource.getBiome(x, y, z);

		final int seaLevel = 64;
		final double dirtDepth = 4;
		IBlockState state = Blocks.AIR.getDefaultState();
		if (density > 0) {
			state = Blocks.STONE.getDefaultState();
			//if the block above would be empty:
			if (density + yGrad <= 0) {
				if (y < seaLevel - 1) {
					state = biome.fillerBlock;
				} else {
					state = biome.topBlock;
				}
				//if density decreases as we go up && density < dirtDepth
			} else if (yGrad < 0 && density < dirtDepth) {
				state = biome.fillerBlock;
			}
		} else if (y < seaLevel) {
			// TODO replace check with GlobalGeneratorConfig.SEA_LEVEL
			state = Blocks.WATER.getDefaultState();
		}
		return new BlockStateInstance(state, x, y, z);
	}

}
