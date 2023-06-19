package net.simplyrin.restartmanager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.simplyrin.restartmanager.command.CommandRestartManager;

/**
 * Created by SimplyRin on 2023/06/19.
 *
 * Copyright (c) 2023 SimplyRin
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class RestartManager extends JavaPlugin {
	
	@Override
	public void onEnable() {
		this.reloadConfig();
		this.reloadMessageConfig();
		
		this.getCommand("restartmanager").setExecutor(new CommandRestartManager(this));
	}
	
	@Override
	public void onDisable() {
		this.cancelTimers();
	}
	
	// config.yml
	@Getter private boolean restartEnabled;
	@Getter private String time;
	@Getter private List<String> commands;
	
	@Getter private boolean countdownEnabled;
	@Getter private List<Integer> broadcastOnSecond;
	
	// configMessage.yml
	@Getter private String restarting;
	@Getter private String countdown;
	
	private Timer scheduleTimer;
	private Timer restartTimer;
	
	public void schedule() {
		if (this.scheduleTimer != null) {
			this.scheduleTimer.cancel();
		}
		
		this.scheduleTimer = null;
		
		int hour = Integer.valueOf(this.time.split("[:]")[0]);
		int minute = Integer.valueOf(this.time.split("[:]")[1]);

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());

		calendar.set(Calendar.HOUR_OF_DAY, hour);
		calendar.set(Calendar.MINUTE, minute);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		
		// 待機前
		calendar.add(Calendar.SECOND, -this.broadcastOnSecond.get(0));
		
		double target = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE) + calendar.get(Calendar.SECOND) * 0.01;
		
		Calendar nowC = Calendar.getInstance();
		double now = nowC.get(Calendar.HOUR_OF_DAY) * 60 + nowC.get(Calendar.MINUTE) + nowC.get(Calendar.SECOND) * 0.01;
		
		// System.out.println("対象: " + target + ", 現在: " + now);
		
		if (target <= now) {
			calendar.add(Calendar.DAY_OF_MONTH, 1);
		}
		
		Date date = calendar.getTime();
		this.info("Restart timer: " + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(date));
		
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				RestartManager.this.restart();
			}
		};
		
		this.scheduleTimer = new Timer(false);
		this.scheduleTimer.schedule(task, date);
	}
	
	public void restart() {
		this.restartTimer = new Timer(false);
		
		TimerTask task = new TimerTask() {
			
			private int count = RestartManager.this.broadcastOnSecond.get(0);
			
			@Override
			public void run() {
				if (RestartManager.this.broadcastOnSecond.contains(this.count)) {
					String countdown = RestartManager.this.countdown;
					
					countdown = countdown.replace("{SECONDS}", String.valueOf(this.count));
					
					RestartManager.this.info(countdown);
					RestartManager.this.broadcast(countdown);
				}
				
				if (this.count == 0) {
					RestartManager.this.info(RestartManager.this.restarting);
					RestartManager.this.broadcast(RestartManager.this.restarting);
					
					RestartManager.this.getServer().getScheduler().runTask(RestartManager.this, () -> {
						for (String command : RestartManager.this.commands) {
							RestartManager.this.getServer().dispatchCommand(RestartManager.this.getServer().getConsoleSender(), command);
						}
					});
					
					RestartManager.this.restartTimer.cancel();
				}
				
				this.count--;
			}
		};
		
		this.restartTimer.schedule(task, 0, 1000);
	}
	
	public void cancelTimers() {
		if (this.scheduleTimer != null) {
			this.scheduleTimer.cancel();
		}
		
		if (this.restartTimer != null) {
			this.restartTimer.cancel();
		}
	}
	
	public void reloadConfig() {
		this.saveDefaultConfig();
		
		super.reloadConfig();
		
		this.restartEnabled = this.getConfig().getBoolean("enabled");
		this.time = this.getConfig().getString("time");
		this.commands = this.getConfig().getStringList("commands");
		
		this.countdownEnabled = this.getConfig().getBoolean("countdown.enabled");
		this.broadcastOnSecond = this.getConfig().getIntegerList("countdown.broadcastOnSecond");
		
		if (this.broadcastOnSecond.isEmpty()) {
			this.broadcastOnSecond = new ArrayList<>(Arrays.asList(60, 30, 10, 5, 4, 3, 2, 1));
		}
		
		Collections.sort(this.broadcastOnSecond, Collections.reverseOrder());
		
		if (this.restartEnabled) {
			this.cancelTimers();
			this.schedule();
		}
	}
	
	private YamlConfiguration messageConfig = null;
	
	public void reloadMessageConfig() {
		File messageConfig = new File(this.getDataFolder(), "configMessage.yml");
		
		if (!messageConfig.exists()) {
			this.saveResource(messageConfig.getName(), false);
		}
		
		this.messageConfig = YamlConfiguration.loadConfiguration(messageConfig);
		
		this.restarting = this.messageConfig.getString("restarting");
		this.countdown = this.messageConfig.getString("countdown");
	}
	
	public void info(String message) {
		this.getServer().getConsoleSender().sendMessage(
				"[" + this.getDescription().getName() + "] " + ChatColor.translateAlternateColorCodes('&', message));
	}
	
	public void broadcast(String message) {
		for (Player player : this.getServer().getOnlinePlayers()) {
			player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
		}
	}

}
