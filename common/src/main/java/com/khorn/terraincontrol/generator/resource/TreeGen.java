package com.khorn.terraincontrol.generator.resource;

import com.khorn.terraincontrol.LocalWorld;
import com.khorn.terraincontrol.TerrainControl;
import com.khorn.terraincontrol.configuration.BiomeConfig;
import com.khorn.terraincontrol.configuration.ConfigFunction;
import com.khorn.terraincontrol.customobjects.CustomObject;
import com.khorn.terraincontrol.customobjects.bo2.BO2;
import com.khorn.terraincontrol.customobjects.bo3.BO3;
import com.khorn.terraincontrol.exception.InvalidConfigException;
import com.khorn.terraincontrol.logging.LogMarker;
import com.khorn.terraincontrol.util.ChunkCoordinate;
import com.khorn.terraincontrol.util.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TreeGen extends Resource
{
	// OTG+
	
    private List<CustomObject> getTrees(String worldName)
    {
    	if(trees.size() == 0 && treeNames.size() > 0)
    	{
	        for (int i = 0; i < treeNames.size(); i++)
	        {	        	
	        	//trees.add(TerrainControl.getCustomObjectManager().getGlobalObjects().parseCustomObject(treeNames.get(i), worldName));
	        	trees.add(TerrainControl.getCustomObjectManager().getGlobalObjects().getObjectByName(treeNames.get(i), worldName));
	        }
    	}
    	return trees;
    }	
	
	//
	
    private final List<Integer> treeChances;
    private final List<String> treeNames;
    private final List<CustomObject> trees;

    public TreeGen(BiomeConfig biomeConfig, List<String> args) throws InvalidConfigException
    {
        super(biomeConfig);
        assureSize(3, args);

        frequency = readInt(args.get(0), 1, 100);

        trees = new ArrayList<CustomObject>();
        treeNames = new ArrayList<String>();
        treeChances = new ArrayList<Integer>();

        for (int i = 1; i < args.size() - 1; i += 2)
        {
            treeNames.add(args.get(i));
            treeChances.add(readInt(args.get(i + 1), 1, 100));
        }
    }

    @Override
    public boolean equals(Object other)
    {
        if (!super.equals(other))
            return false;
        if (other == null)
            return false;
        if (other == this)
            return true;
        if (getClass() != other.getClass())
            return false;
        final TreeGen compare = (TreeGen) other;
        return (this.trees == null ? this.trees == compare.trees
                : this.trees.equals(compare.trees))
                && (this.treeNames == null ? this.treeNames == compare.treeNames
                        : this.treeNames.equals(compare.treeNames))
                && (this.treeChances == null ? this.treeChances == compare.treeChances
                        : this.treeChances.equals(compare.treeChances));
    }

    @Override
    public int getPriority()
    {
        return -31;
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 53 * hash + super.hashCode();
        hash = 53 * hash + (this.trees != null ? this.trees.hashCode() : 0);
        hash = 53 * hash + (this.treeNames != null ? this.treeNames.hashCode() : 0);
        hash = 53 * hash + (this.treeChances != null ? this.treeChances.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean isAnalogousTo(ConfigFunction<BiomeConfig> other)
    {
        if (getClass() == other.getClass())
        {
            try
            {
                TreeGen otherO = (TreeGen) other;
                return otherO.treeNames.size() == this.treeNames.size() && otherO.treeNames.containsAll(this.treeNames);
            } catch (Exception ex)
            {
                TerrainControl.log(LogMarker.WARN, ex.getMessage());
            }
        }
        return false;
    }

    @Override
    public String toString()
    {
        String output = "Tree(" + frequency;
        for (int i = 0; i < treeNames.size(); i++)
        {
            output += "," + treeNames.get(i) + "," + treeChances.get(i);
        }
        return output + ")";
    }

    @Override
    public void spawn(LocalWorld world, Random random, boolean villageInChunk, int x, int z)
    {
        // Left blank, as process() already handles this
    }
    
    @Override
    protected void spawnInChunk(LocalWorld world, Random random, boolean villageInChunk, ChunkCoordinate chunkCoord)
    {    	   	
        for (int i = 0; i < frequency; i++)
        {        	
            for (int treeNumber = 0; treeNumber < treeNames.size(); treeNumber++)
            {           	            
                if (random.nextInt(100) < treeChances.get(treeNumber))
                {                	
                    int x = chunkCoord.getBlockXCenter() + random.nextInt(ChunkCoordinate.CHUNK_X_SIZE);
                    int z = chunkCoord.getBlockZCenter() + random.nextInt(ChunkCoordinate.CHUNK_Z_SIZE);               	
                    
                    CustomObject tree = getTrees(world.getName()).get(treeNumber);                   
                   
                    if(tree == null)
                    {
                		if(TerrainControl.getPluginConfig().SpawnLog)
                		{
                			BiomeConfig biomeConfig = world.getBiome(chunkCoord.getChunkX() * 16 + 15, chunkCoord.getChunkZ() * 16 + 15).getBiomeConfig();
                			TerrainControl.log(LogMarker.WARN, "Error: Could not find BO3 for Tree in biome " + biomeConfig.getName() + ". BO3: " + treeNames.get(treeNumber));
                		}
                		//continue;
                		throw new RuntimeException();
                    }
                    
                    if(tree instanceof BO2 || tree instanceof BO3)
                    {   
                		if(
            				(tree instanceof BO2 && ((BO2)tree).spawnAsTree(world, random, x, z)) ||
            				(tree instanceof BO3 && ((BO3)tree).spawnAsTree(world, random, x, z))
        				)
                		{
        	                // Success!
        	                break;
                		}
                    } else {
                        int y = world.getHighestBlockYAt(x, z);
                        Rotation rotation = Rotation.getRandomRotation(random);

                        if (tree.trySpawnAt(world, random, rotation, x, y, z))
                        {
        	                // Success!
                        	break;
                        }                    
                    }
                }
            }
        }    		
    }
}
