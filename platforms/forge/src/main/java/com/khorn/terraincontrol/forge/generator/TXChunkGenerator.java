package com.khorn.terraincontrol.forge.generator;

import static com.khorn.terraincontrol.util.ChunkCoordinate.CHUNK_X_SIZE;
import static com.khorn.terraincontrol.util.ChunkCoordinate.CHUNK_Z_SIZE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import com.khorn.terraincontrol.LocalBiome;
import com.khorn.terraincontrol.LocalMaterialData;
import com.khorn.terraincontrol.TerrainControl;
import com.khorn.terraincontrol.configuration.ConfigProvider;
import com.khorn.terraincontrol.configuration.WorldConfig;
import com.khorn.terraincontrol.configuration.WorldConfig.TerrainMode;
import com.khorn.terraincontrol.customobjects.bo3.BlockFunction;
import com.khorn.terraincontrol.customobjects.bo3.ModDataFunction;
import com.khorn.terraincontrol.forge.ForgeMaterialData;
import com.khorn.terraincontrol.forge.ForgeWorld;
import com.khorn.terraincontrol.forge.TXPlugin;
import com.khorn.terraincontrol.generator.ChunkProviderTC;
import com.khorn.terraincontrol.generator.ObjectSpawner;
import com.khorn.terraincontrol.generator.biome.OutputType;
import com.khorn.terraincontrol.logging.LogMarker;
import com.khorn.terraincontrol.util.ChunkCoordinate;
import com.khorn.terraincontrol.util.minecraftTypes.DefaultMaterial;

import net.minecraft.block.BlockGravel;
import net.minecraft.block.BlockSand;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biome.SpawnListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraftforge.fml.common.event.FMLInterModComms;

public class TXChunkGenerator implements IChunkGenerator
{
	// OTG+ 
	
	public ArrayList<Object[]> PopulatedChunks;
	
    int lastx2 = 0;
    int lastz2 = 0;
    int chunkCacheSize = 256;
    //int chunkCacheSizeDuringPreGeneration = 1024;   
    LinkedHashMap<ChunkCoordinate, Object[]> chunkCache = new LinkedHashMap<ChunkCoordinate, Object[]>();
    //LinkedHashMap<ChunkCoordinate, BlockFunction[]> BlockColumnCache = new LinkedHashMap<ChunkCoordinate, BlockFunction[]>();
    long lastCacheClearedTime = System.currentTimeMillis();
    
	//	
	
    private ForgeWorld world;
    private World worldHandle;
    private boolean TestMode = false;

    private ChunkProviderTC generator;
    public ObjectSpawner spawner;

    /** 
     * Used in {@link #fillBiomeArray(Chunk)}, to avoid creating
     * new int arrays.
     */
    private int[] biomeIntArray;

    public TXChunkGenerator(ForgeWorld _world)
    {   	   	
        this.world = _world;
        this.worldHandle = _world.getWorld();

        this.TestMode = this.world.getConfigs().getWorldConfig().ModeTerrain == WorldConfig.TerrainMode.TerrainTest;

        this.generator = new ChunkProviderTC(this.world.getConfigs(), this.world);
        this.spawner = new ObjectSpawner(this.world.getConfigs(), this.world);
        
        // OTG +
        this.PopulatedChunks = new ArrayList<Object[]>();
    }

    private ForgeChunkBuffer chunkBuffer;
    @Override
    public Chunk generateChunk(int chunkX, int chunkZ)
    {
    	//TerrainControl.log(LogMarker.INFO, "provideChunk X" + chunkX + " Z" + chunkZ);
    	
    	ChunkCoordinate chunkCoords = ChunkCoordinate.fromChunkCoords(chunkX, chunkZ);
    	boolean bFound = false;
    	synchronized(PopulatedChunks)
    	{
			for(Object[] chunkCoord : PopulatedChunks)
			{
				if((Integer)chunkCoord[0] == chunkX && (Integer)chunkCoord[1] == chunkZ)
				{
					bFound = true;
				}
			}
			if(!bFound)
			{
				PopulatedChunks.add(new Object[] { chunkX, chunkZ });
			}
    	}
		
		if(bFound)
		{
			Chunk chunk = world.getChunk(chunkCoords.getBlockX(), chunkCoords.getBlockZ(), true);
			if(chunk == null)
			{				
				if(world.IsInsideWorldBorder(chunkCoords, false))
				{					
					// Can happen when chunkExists() in this.world.getChunk() mistakenly returns false
					// This could potentially cause an infinite loop but than't can't be disallowed looping because of async calls
					// to ProvideChunk() by updateBlocks() on server tick.
					chunk = this.world.getWorld().getChunkFromChunkCoords(chunkX, chunkZ);    						
					
					if(chunk == null)
					{
						throw new RuntimeException();
					}
					TerrainControl.log(LogMarker.INFO, "Double population prevented");
				}
			}
			if(chunk != null)
			{
				TerrainControl.log(LogMarker.INFO, "Double population prevented");
				return chunk;
			} else {
				TerrainControl.log(LogMarker.INFO, "Double population could not be prevented for chunk X" + chunkX + " Z" + chunkZ);
			}
		}
		
		Chunk chunk = getBlocks(chunkX, chunkZ, true);
		return chunk;
    }
    
    public int getHighestBlockInCurrentlyGeneratingChunk(int blockX, int blockZ)
    {
    	for(int y = 255; y > 0; y--)
    	{
    		ForgeMaterialData material = (ForgeMaterialData)chunkBuffer.getBlock(blockX, y, blockZ);
    		if(!material.isAir())
    		{
    			return y;
    		}
    	}
    	
    	return 0;
    }

    /**
     * Fills the biome array of a chunk with the proper saved ids (no
     * generation ids).
     * @param chunk The chunk to fill the biomes of.
     */
    private void fillBiomeArray(Chunk chunk)
    {
        byte[] chunkBiomeArray = chunk.getBiomeArray();
        ConfigProvider configProvider = this.world.getConfigs();
        this.biomeIntArray = this.world.getBiomeGenerator().getBiomes(this.biomeIntArray, chunk.x * CHUNK_X_SIZE, chunk.z * CHUNK_Z_SIZE, CHUNK_X_SIZE, CHUNK_Z_SIZE, OutputType.DEFAULT_FOR_WORLD);

        for (int i = 0; i < chunkBiomeArray.length; i++)
        {
            int generationId = this.biomeIntArray[i];
               
            // For forge make sure all dimensions are queried since the biome we're looking for may be owned by another dimension
            LocalBiome biome = TerrainControl.isForge ? TerrainControl.getBiomeAllWorlds(generationId) : configProvider.getBiomeByIdOrNull(generationId);
        	
        	chunkBiomeArray[i] = (byte) biome.getIds().getSavedId();
        }
    }
    
    @Override
    public void populate(int chunkX, int chunkZ)
    {   
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromChunkCoords(chunkX, chunkZ);
    	if(this.TestMode || !world.IsInsideWorldBorder(chunkCoord, false))
        {
    		world.ClearChunkCache();
            return;
        }         		
   	
    	//TerrainControl.log(LogMarker.INFO, "Populate X" + x + " Z" + z);
    	
        //if(1 == 1) { return; }
    	
        BlockSand.fallInstantly = true;
        BlockGravel.fallInstantly = true;
    	
        if(!this.spawner.processing)
        {
	        this.spawner.populatingX = chunkX;
	        this.spawner.populatingZ = chunkZ;
        } else {
			// This happens when:
			// This chunk was populated because of a block being spawned on the
			// other side of the edge of this chunk,
			// the block performed a block check inside this chunk upon being
			// placed (like a torch looking for a wall to stick to)
			// This means that we must place any BO3 queued for this chunk
			// because the block being spawned might need to interact with it
			// (spawn the wall for the torch to stick to).
			// Unfortunately this means that this chunk will not get a call to
			// populate() via the usual population
			// mechanics where we populate 4 BO3's at once in a 2x2 chunks area
			// and then spawn resources (ore, trees, lakes)
			// on top of that. Hopefully the neighbouring chunks do get spawned
			// normally and cover the 2x2 areas this chunk is part of
			// with enough resources that noone notices some are missing...

			// This can also happen when the server decides to provide and/or
			// populate a chunk that has already been provided/populated before,
			// which seems like a bug.	        	
        	//throw new NotImplementedException();
        }
    
        fixSpawnChunk();
    
        this.spawner.populate(chunkCoord);
        
        BlockSand.fallInstantly = false;
        BlockGravel.fallInstantly = false;

    	//ChunkCoordinate chunkCoord = ChunkCoordinate.fromChunkCoords(x, z);
        //this.world.generateSkylightMap(chunkCoord.getBlockX(),chunkCoord.getBlockZ());
    	
        HashMap<String,ArrayList<ModDataFunction>> MessagesPerMod = world.GetWorldSession().GetModDataForChunk(chunkCoord);
        if(MessagesPerMod == null && world.getConfigs().getWorldConfig().IsOTGPlus)
        {
    		if(!world.getStructureCache().structureCache.containsKey(chunkCoord))
    		{    			
    			if(!world.getStructureCache().worldInfoChunks.containsKey(chunkCoord))
    			{	    				
    				throw new RuntimeException();
    			}
    		}
    		TerrainControl.log(LogMarker.INFO, "This exception seems to be a fluke and occurs rarely. If you find a way to re-create it please tell me!");
        	throw new RuntimeException();
        }
        if(MessagesPerMod != null && MessagesPerMod.entrySet().size() > 0)
        {        	
        	for(Entry<String, ArrayList<ModDataFunction>> modNameAndData : MessagesPerMod.entrySet())
        	{
        		String messageString = "";
				if(modNameAndData.getKey().equals("OTG"))
				{        	    		
	    			for(ModDataFunction modData : modNameAndData.getValue())
	    			{    	   	    				    	    				
						String[] paramString2 = modData.modData.split("\\/");
						
						if(paramString2.length > 1)
						{
							if(paramString2[0].equals("mob"))
							{
								boolean autoSpawn = paramString2.length > 4 ? Boolean.parseBoolean(paramString2[4]) : false;
	    	    				if(autoSpawn)
	    	    				{
	    	    					messageString += "[" + modData.x + "," + modData.y + "," + modData.z + "," + modData.modData + "]";	
	    	    				} 
							}    							
						}       					
	    			}
				} else {
	    			for(ModDataFunction modData : modNameAndData.getValue())
	    			{    				
    					messageString += "[" + modData.x + "," + modData.y + "," + modData.z + "," + modData.modData + "]";
	    			}
				}
    			if(messageString.length() > 0)
    			{
    				// Send messages to any mods listening
    				FMLInterModComms.sendRuntimeMessage(TXPlugin.instance, modNameAndData.getKey(), "ModData", "[" + "[" + world.getName() + "," + chunkX + "," + chunkZ + "]" + messageString + "]");
    			}
        	}
        }
        
        Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		long allocatedMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();        
		    		
        if((!world.GetWorldSession().getPreGeneratorIsRunning() && chunkCache.entrySet().size() > chunkCacheSize) || (freeMemory + (maxMemory - allocatedMemory) <= (maxMemory * 0.25)))        	
        {        	
        	// Don't clear cache more than once every ten seconds
        	// ServerTick.cs onTick() will reboot the server if memory is running out
    		//if((System.currentTimeMillis() - lastCacheClearedTime) / 1000 >= 10)
        	{
	        	TerrainControl.log(LogMarker.DEBUG, "Clearing ChunkProvider cache");
	        	chunkCache.clear();
	        	//System.gc(); // TODO: Check if this is necessary for the pre-generator
	        	////chunkCache.remove(chunkCache.keySet().iterator().next());
	        	lastCacheClearedTime = System.currentTimeMillis();
        	}
        }
        
		world.ClearChunkCache();
    }    

    @Override
    public List<SpawnListEntry> getPossibleCreatures(EnumCreatureType paramaca, BlockPos blockPos)
    {
        WorldConfig worldConfig = this.world.getConfigs().getWorldConfig();
        Biome biomeBase = this.worldHandle.getBiomeForCoordsBody(blockPos);

        if (worldConfig.rareBuildingsEnabled)
        {
            if (paramaca == EnumCreatureType.MONSTER && this.world.rareBuildingGen.isSwampHutAtLocation(blockPos))
            {
                return this.world.rareBuildingGen.getMonsterSpawnList();
            }
        }
        if (worldConfig.oceanMonumentsEnabled)
        {
            if (paramaca == EnumCreatureType.MONSTER && this.world.oceanMonumentGen.isPositionInStructure(this.worldHandle, blockPos))
            {
                return this.world.oceanMonumentGen.getMonsterSpawnList();
            }
        }

        return biomeBase.getSpawnableList(paramaca);
    }

    @Override
    public void recreateStructures(Chunk chunkIn, int chunkX, int chunkZ)
    {
        // recreateStructures
        WorldConfig worldConfig = this.world.getConfigs().getWorldConfig();
           
        if (worldConfig.mineshaftsEnabled)
        {
            this.world.mineshaftGen.generate(this.world.getWorld(), chunkX, chunkZ, null);
        }
        if (worldConfig.villagesEnabled)
        {
            this.world.villageGen.generate(this.world.getWorld(), chunkX, chunkZ, null);
        }
        if (worldConfig.strongholdsEnabled)
        {
            this.world.strongholdGen.generate(this.world.getWorld(), chunkX, chunkZ, null);
        }
        if (worldConfig.rareBuildingsEnabled)
        {
            this.world.rareBuildingGen.generate(this.world.getWorld(), chunkX, chunkZ, null);
        }
        if (worldConfig.netherFortressesEnabled)
        {
            this.world.netherFortressGen.generate(this.world.getWorld(), chunkX, chunkZ, null);
        }
        if (worldConfig.oceanMonumentsEnabled)
        {
            this.world.oceanMonumentGen.generate(this.world.getWorld(), chunkX, chunkZ, null);
        }
        if (worldConfig.woodLandMansionsEnabled)
        {
            this.world.woodLandMansionGen.generate(this.world.getWorld(), chunkX, chunkZ, null);
        }
    }

    @Override
    public boolean generateStructures(Chunk chunkIn, int x, int z)
    {
        return false;
    } 	
    
    @Override
    public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos blockPos, boolean p_180513_4_)
    {
    	//if(!this.mapFeaturesEnabled == null)
    	{
	        // Gets the nearest stronghold
	        if (("Stronghold".equals(structureName)) && (this.world.strongholdGen != null))
	        {
	            return this.world.strongholdGen.getNearestStructurePos(worldIn, blockPos, p_180513_4_);
	        }
	        if (("Mansion".equals(structureName)) && (this.world.woodLandMansionGen != null))
	        {
	            return this.world.woodLandMansionGen.getNearestStructurePos(worldIn, blockPos, p_180513_4_);
	        }
	        if (("Monument".equals(structureName)) && (this.world.oceanMonumentGen != null))
	        {
	            return this.world.oceanMonumentGen.getNearestStructurePos(worldIn, blockPos, p_180513_4_);
	        }
	        if (("Village".equals(structureName)) && (this.world.villageGen != null))
	        {
	            return this.world.villageGen.getNearestStructurePos(worldIn, blockPos, p_180513_4_);
	        }
	        if (("Mineshaft".equals(structureName)) && (this.world.mineshaftGen != null))
	        {
	            return this.world.mineshaftGen.getNearestStructurePos(worldIn, blockPos, p_180513_4_);
	        }
	        if (("Temple".equals(structureName)) && (this.world.rareBuildingGen != null))
	        {
	            return this.world.rareBuildingGen.getNearestStructurePos(worldIn, blockPos, p_180513_4_);
	        }
    	}
    	
        return null;
    }
    
    // OTG+   
           
    public BlockFunction[] getBlockColumnInUnloadedChunk(int x, int z)
    {
    	//TerrainControl.log(LogMarker.INFO, "getBlockColumnInUnloadedChunk X" + x + " Z" + z);
    	
    	lastx2 = x;
    	lastz2 = z;
    	
    	ChunkCoordinate chunkCoord = ChunkCoordinate.fromBlockCoords(x, z);
    	int chunkX = chunkCoord.getChunkX();
    	int chunkZ = chunkCoord.getChunkZ();
    	
    	Object[] chunkCacheEntry = chunkCache.get(chunkCoord);
    	
    	Chunk chunk = null;
    	LinkedHashMap<ChunkCoordinate, BlockFunction[]> blockColumnCache = null;
    	BlockFunction[] cachedColumn = null;
    	if(chunkCacheEntry != null)
    	{
    		chunk = (Chunk)chunkCacheEntry[0];
    		blockColumnCache = (LinkedHashMap<ChunkCoordinate, BlockFunction[]>)chunkCacheEntry[1];
    		cachedColumn = blockColumnCache.get(ChunkCoordinate.fromChunkCoords(x,z));
    	}
    	if(cachedColumn != null)
    	{
    		return cachedColumn;
    	}
    	    	   	           	   	    	
    	if(chunk == null)
    	{
        	chunk = new Chunk(this.worldHandle, chunkX, chunkZ);
            
        	if(world.IsInsideWorldBorder(chunkCoord, true))
            {
	    		ForgeChunkBuffer chunkBuffer = new ForgeChunkBuffer(chunkCoord);
	    		this.generator.generate(chunkBuffer);
	    		 
	    		chunk = chunkBuffer.toChunk(this.worldHandle);        		
            }
        	blockColumnCache = new LinkedHashMap<ChunkCoordinate, BlockFunction[]>();
        	chunkCache.put(ChunkCoordinate.fromChunkCoords(chunkX,chunkZ), new Object[] { chunk, blockColumnCache });
    	}
    	
		// Get internal coordinates for block in chunk       
    	int blockX = x &= 0xF;
    	int blockZ = z &= 0xF;
    	
        BlockFunction[] blocksInColumn = new BlockFunction[256];        
        for(int y = 0; y < 256; y++)
        {
        	BlockFunction block = new BlockFunction();
        	block.x = x;
        	block.y = y;
        	block.z = z;
        	IBlockState blockInChunk = chunk.getBlockState(new BlockPos(blockX, y, blockZ));
        	if(blockInChunk != null)
        	{
        		block.material = ForgeMaterialData.ofMinecraftBlockState(blockInChunk);
	        	blocksInColumn[y] = block;
        	} else {
        		break;
        	}
        }       
        blockColumnCache.put(ChunkCoordinate.fromChunkCoords(lastx2,lastz2), blocksInColumn);
        
        return blocksInColumn;
    }
    
    public LocalMaterialData getMaterialInUnloadedChunk(int x, int y, int z)
    {    	    	   	   	    	
    	BlockFunction[] blockColumn = getBlockColumnInUnloadedChunk(x,z);    	
        return blockColumn[y].material;
    }
    
    public int getHighestBlockYInUnloadedChunk(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow)    
    {		    	   	
    	int height = -1;
    	
    	BlockFunction[] blockColumn = getBlockColumnInUnloadedChunk(x,z);
    	
        for(int y = 255; y > -1; y--)
        {
        	ForgeMaterialData material = (ForgeMaterialData) blockColumn[y].material;
        	boolean isLiquid = material.isLiquid();
        	boolean isSolid = material.isSolid() || (!ignoreSnow && material.toDefaultMaterial().equals(DefaultMaterial.SNOW));
        	if(!(isLiquid && ignoreLiquid))
        	{
            	if((findSolid && isSolid) || (findLiquid && isLiquid))
        		{           		
            		return y;
        		}
            	if((findSolid && isLiquid) || (findLiquid && isSolid))
            	{
            		return -1;
            	}
        	}
        }
    	return height;    	
    }
    
    boolean firstRun = true; // The first run is used by MC to check for suitable locations for the spawn location. For some reason the spawn location must be on grass.
    ArrayList<LocalMaterialData> originalBlocks = new ArrayList<LocalMaterialData>(); // Don't need to store coords, will place the blocks back in the same order we got them so coords can be inferred
    ChunkCoordinate spawnChunk;
    boolean spawnChunkFixed = false;
    
    public void fixSpawnChunk()
    {
    	if(!spawnChunkFixed && !firstRun)
    	{    		
    		spawnChunkFixed = true;
			int i = 0;			
			for(int x = 0; x < 15; x++)
			{
				for(int z = 0; z < 15; z++)
				{
					if(!originalBlocks.get(i).toDefaultMaterial().equals(DefaultMaterial.AIR) || !originalBlocks.get(i + 1).toDefaultMaterial().equals(DefaultMaterial.AIR))
					{
						world.setBlock(spawnChunk.getBlockX() + x, 63, spawnChunk.getBlockZ() + z, originalBlocks.get(i), null, true);
						world.setBlock(spawnChunk.getBlockX() + x, 64, spawnChunk.getBlockZ() + z, originalBlocks.get(i + 1), null, true);
					} else {					
						for(int h = 62; h > 0; h++)
						{
							if(!world.getMaterial(spawnChunk.getBlockX() + x, h, spawnChunk.getBlockZ() + z, true).toDefaultMaterial().equals(DefaultMaterial.AIR))
							{
								world.setBlock(spawnChunk.getBlockX() + x, 63, spawnChunk.getBlockZ() + z, originalBlocks.get(i), null, true);
								world.setBlock(spawnChunk.getBlockX() + x, 64, spawnChunk.getBlockZ() + z, originalBlocks.get(i + 1), null, true);
								break;
							}
						}
					}
					i += 2;
				}
			}
    	}
    }    
    
    public Chunk getBlocks(int chunkX, int chunkZ, boolean provideChunk)
    {      	
    	//TerrainControl.log(LogMarker.INFO, "getBlocks X" + chunkX + " Z" + chunkZ + " " + provideChunk);
    	
    	Object[] chunkCacheEntry = chunkCache.get(ChunkCoordinate.fromChunkCoords(chunkX,chunkZ));
    	Chunk chunk = null;
    	if(chunkCacheEntry != null)
    	{
    		chunk = (Chunk)chunkCacheEntry[0];
    	}
    	
    	if(chunk == null)
    	{
    		chunk = new Chunk(this.worldHandle, chunkX, chunkZ);           	
    		
	    	if(world.IsInsideWorldBorder(ChunkCoordinate.fromChunkCoords(chunkX, chunkZ), false))
	        {
	    		ChunkCoordinate chunkCoord = ChunkCoordinate.fromChunkCoords(chunkX, chunkZ);
	    		ForgeChunkBuffer chunkBuffer = new ForgeChunkBuffer(chunkCoord);
	    		this.generator.generate(chunkBuffer);
	    		
	    		// Before starting terrain generation MC tries to find a suitable spawn point. For some reason it looks for a grass block with an air block above it.
	    		// To prevent MC from looking in many chunks (if there is no grass block nearby) and causing them to be populated place grass in the first requested chunk
	    		// cache the original blocks so that they can be placed back when proper world generation starts.
	    		if(firstRun)
	    		{
	    			firstRun = false;
	    			spawnChunk = chunkCoord;
	    			for(int x = 0; x < 15; x++)
	    			{
	    				for(int z = 0; z < 15; z++)
	    				{	    					
	    					originalBlocks.add(chunkBuffer.getBlock(x, 63, z));
	    					originalBlocks.add(chunkBuffer.getBlock(x, 64, z));    					
	    					
	    					chunkBuffer.setBlock(x, 63, z, TerrainControl.toLocalMaterialData(DefaultMaterial.GRASS, 0));
	    					chunkBuffer.setBlock(x, 64, z, TerrainControl.toLocalMaterialData(DefaultMaterial.AIR, 0));
	    				}
	    			}
	    		}
	    		chunk = chunkBuffer.toChunk(this.worldHandle);
	
		        fillBiomeArray(chunk);
		        //if(world.getConfigs().getWorldConfig().ModeTerrain == TerrainMode.TerrainTest) 
		        {
		        	chunk.generateSkylightMap(); // Normally chunks lit in the ObjectSpawner after finishing their population step, TerrainTest skips the population step though so light blocks here.
		        }
		        chunkBuffer = null; // Not sure what this is for, hoping to have it GC'd asap?
	        }
    	} else {
        	if(world.IsInsideWorldBorder(ChunkCoordinate.fromChunkCoords(chunkX, chunkZ), false))
	        {	
		        fillBiomeArray(chunk);
		        if(world.getConfigs().getWorldConfig().ModeTerrain == TerrainMode.TerrainTest)
		        {
		        	chunk.generateSkylightMap(); // Normally chunks lit in the ObjectSpawner after finishing their population step, TerrainTest skips the population step though so light blocks here.
		        }
	        }
        	chunkCache.remove(ChunkCoordinate.fromChunkCoords(chunkX,chunkZ));
    	}
    	
    	return chunk;
    }           
}
