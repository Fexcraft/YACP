package net.fexcraft.mod.yacp;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fexcraft.app.json.JsonHandler;
import net.fexcraft.app.json.JsonMap;
import net.fexcraft.lib.common.math.Time;
import net.fexcraft.mod.uni.world.WrapperHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;

/**
 * @author Ferdinand Calo' (FEX___96)
 */
public class Yacp implements ModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("yacp");
	private static HashMap<ResourceLocation, PregenTask> TASKS = new HashMap<>();
	private static HashMap<ResourceLocation, File> FOLDERS = new HashMap<>();
	private static PregenTask TASK;
	public static YacpConfig CONFIG;
	private float accu;
	private float incr;
	private float iacc;
	private long lasttick;
	private long newtick;
	private long tickms;
	private int[] epa = new int[2];
	private int ticks;
	private int proc;
	private int skip;

	@Override
	public void onInitialize() {
		CONFIG = new YacpConfig(new File(FabricLoader.getInstance().getGameDirectory(), "/config/yacp.json"));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = server.overworld();
			File fol = WrapperHolder.getWorldFolder(WrapperHolder.getWorld(level), "yacp");
			FOLDERS.put(level.dimension().location(), fol);
			//LOGGER.info(level.dimension().location() + " " + fol.getAbsolutePath());
			try{
				File file = new File(fol, "task.json");
				if(!file.exists()) JsonHandler.print(file, new JsonMap());
				TASKS.put(level.dimension().location(), new PregenTask(fol, file, JsonHandler.parse(file)));
			}
			catch(Exception e){
				e.printStackTrace();
			}
			TASK = TASKS.get(level.dimension().location());
			if(TASK.complete){
				LOGGER.info("Task is marked as completed, not starting.");
			}
			else{
				LOGGER.info("Starting pre-gen task.");
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FOLDERS.clear();
			TASK.save();
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			if(TASK.complete) return;
			if(lasttick == 0){
				lasttick = Time.getDate();
				return;
			}
			newtick = Time.getDate();
			long diff = newtick - lasttick;
			lasttick = newtick;
			tickms += diff;
			iacc += (int)diff;
			if(tickms <= 1000){
				incr = 50f / (int)iacc;
				if(incr > 1) incr = 1;
				accu += incr;
				iacc = 0;
			}
			while(accu >= 1){
				TASK.work(server.overworld(), epa);
				proc += epa[0];
				skip += epa[1];
				accu -= 1;
			}
			if(++ticks > 20){
				LOGGER.info("Procesed " + proc + "ck/s and skipped " + skip + " in ~" + tickms + "ms. " + ((TASK.curr / TASK.total) * 100) + "% (" + TASK.curr + "/" + TASK.total + ")");
				tickms = 0;
				ticks = 0;
				proc = 0;
				skip = 0;
				accu = 0;
			}
		});
	}

}