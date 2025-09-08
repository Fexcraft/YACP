package net.fexcraft.mod.yacp;

import net.fexcraft.app.json.JsonArray;
import net.fexcraft.app.json.JsonHandler;
import net.fexcraft.app.json.JsonMap;
import net.fexcraft.lib.common.math.Time;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.*;
import java.util.HashMap;

/**
 * @author Ferdinand Calo' (FEX___96)
 */
public class PregenTask {

	private HashMap<Integer, RegCache> regs = new HashMap<>();
	private final File file;
	private final File root;
	public boolean complete;
	private int sx, sz;
	private int ex, ez;
	private int cx, cz;
	private int exist;
	private int proc;
	private int save;
	public float total;
	public float curr;

	public PregenTask(File folder, File file, JsonMap map){
		this.root = folder;
		this.file = file;
		complete = map.getBoolean("Complete", false);
		if(map.has("Start")){
			JsonArray arr = map.getArray("Start");
			sx = arr.get(0).integer_value();
			sz = arr.get(1).integer_value();
			arr = map.getArray("End");
			ex = arr.get(0).integer_value();
			ez = arr.get(1).integer_value();
			arr = map.getArray("At");
			cx = arr.get(0).integer_value();
			cz = arr.get(1).integer_value();
		}
		else{
			sx = -8;
			sz = -8;
			ex = 8;
			ez = 8;
			cx = sx;
			cz = sz;
		}
		total = (ex - sx) * (ez - sz);
	}

	public void save(){
		JsonMap map = new JsonMap();
		for(RegCache cache : regs.values()){
			if(!cache.upd[0]) continue;
			try{
				FileOutputStream stream = new FileOutputStream(new File(root, cache.x + "_" + cache.z + ".yacp"));
				byte[] bytes = new byte[32 * 32];
				for(int x = 0; x < 32; x++){
					for(int z = 0; z < 32; z++){
						bytes[x * 32 + z] = (byte)(cache.cks[x][z] ? 64 : -64);
					}
				}
				stream.write(bytes);
				stream.flush();
				stream.close();
				cache.upd[0] = false;
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		map.add("Start", new JsonArray(sx, sz));
		map.add("End", new JsonArray(ex, ez));
		map.add("At", new JsonArray(cx, cz));
		map.add("Processed", proc);
		map.add("Existing", exist);
		map.add("Complete", complete);
		map.add("Saved", Time.getAsString(Time.getDate()));
		JsonHandler.print(file, map);
	}

	public void work(ServerLevel level, int[] epa){
		epa[0] = epa[1] = 0;
		if(complete) return;
		while(wasProcessed(cx, cz) && !complete){
			epa[1]++;
			incrIdx();
			if(epa[1] >= 128) break;
		}
		if(!wasProcessed(cx, cz) && !complete){
			level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
			markProcessed(cx, cz);
			incrIdx();
			epa[0]++;
		}
		curr = (cz - sz) * (ex - sx) + cx;
		save++;
		if(save >= 16){
			save();
			save = 0;
			if(proc > 256){
				regs.clear();
			}
		}
	}

	private void incrIdx(){
		if(cx < ex){
			cx++;
		}
		else{
			cx = sx;
			cz++;
		}
		if(cz >= ez){
			complete = true;
			save();
			Yacp.LOGGER.info("Chunk pre-gen complete for area from " + sx + ", " + sz + " to " + ex + ", " + ez);
			Yacp.LOGGER.info(proc + " generated, " + exist + " skipped.");
		}
	}

	private boolean wasProcessed(int cx, int cz){
		int rx = (int)Math.floor(cx / 32.0), rz = (int)Math.floor(cz / 32.0);
		int key = ChunkPos.hash(rx, rz);
		checkRegion(key, rx, rz);
		boolean bool = regs.get(key).cks[Math.abs(cx % 32)][Math.abs(cz % 32)];
		if(bool) exist++;
		return bool;
	}

	private void markProcessed(int cx, int cz){
		int rx = (int)Math.floor(cx / 32.0), rz = (int)Math.floor(cz / 32.0);
		int key = ChunkPos.hash(rx, rz);
		checkRegion(key, rx, rz);
		regs.get(key).cks[Math.abs(cx % 32)][Math.abs(cz % 32)] = true;
		regs.get(key).upd[0] = true;
		proc++;
	}

	private void checkRegion(int key, int rx, int rz){
		if(regs.containsKey(key)) return;
		RegCache cache = new RegCache(rx, rz, new boolean[32][32], new boolean[]{ true });
		File file = new File(root, cache.x + "_" + cache.z + ".yacp");
		if(file.exists()){
			try{
				FileInputStream stream = new FileInputStream(file);
				byte[] bytes = stream.readAllBytes();
				stream.close();
				for(int x = 0; x < 32; x++){
					for(int z = 0; z < 32; z++){
						cache.cks[x][z] = bytes[x * 32 + z] > 0;
					}
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		regs.put(key, cache);
	}

	private static record RegCache(int x, int z, boolean[][] cks, boolean[] upd){}

}
