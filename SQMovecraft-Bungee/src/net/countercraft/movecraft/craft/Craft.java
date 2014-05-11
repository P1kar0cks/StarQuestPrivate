/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.async.AsyncManager;
import net.countercraft.movecraft.async.detection.DetectionTask;
import net.countercraft.movecraft.async.rotation.RotationTask;
import net.countercraft.movecraft.async.translation.TranslationTask;
import net.countercraft.movecraft.async.translation.TranslationTaskData;
import net.countercraft.movecraft.event.CraftPilotEvent;
import net.countercraft.movecraft.event.CraftSyncTranslateEvent;
import net.countercraft.movecraft.utils.GunUtils;
import net.countercraft.movecraft.utils.MovecraftLocation;
import net.countercraft.movecraft.utils.Rotation;
import net.countercraft.movecraft.utils.ShipMoveTask;
import net.countercraft.movecraft.utils.WarpUtils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class Craft {
	private int[][][] hitBox;
	private final CraftType type;
	private MovecraftLocation[] blockList;
	private World w;
	public AtomicBoolean processing = new AtomicBoolean();
	private int minX, minZ;
	public int xDist, yDist, zDist;
	public ArrayList<String> playersWithBedSpawnsOnShip = new ArrayList<String>();
	public boolean shipAttemptingTeleport = false;
	//these values are related to Autopilot, not acceleration. bad var names.
	public int vX, vZ;
	public boolean isAutopiloting;
	public ArrayList<Player> playersRiding = new ArrayList<Player>();
	public int warpCoordsX;
	public int warpCoordsZ;
	public Location originalPilotLoc = new Location(w,0,0,0);
	public Player pilot;
	public boolean sneakPressed;
	
	private int velocity = 0;
	private int steps = 0;
	
	private ShipMoveTask moveTask;
	
	public Craft( CraftType type, World world ) {
		this.type = type;
		this.w = world;
		this.blockList = new MovecraftLocation[1];
		xDist = 0;
		yDist = 0;
		zDist = 0;
	}

/*	public boolean isNotProcessing() {
		return !processing.get();
	}

	public void setProcessing( boolean processing ) {
		this.processing.set( processing );
	}*/

	public MovecraftLocation[] getBlockList() {
		synchronized ( blockList ) {
			return blockList.clone();
		}
	}

	public void setBlockList( MovecraftLocation[] blockList ) {
		synchronized ( this.blockList ) {
			this.blockList = blockList;
		}
	}

	public CraftType getType() {
		return type;
	}

	public World getW() {
		return w;
	}

	public int[][][] getHitBox() {
		return hitBox;
	}

	public void setHitBox( int[][][] hitBox ) {
		this.hitBox = hitBox;
	}

	public void detect( String playerName, MovecraftLocation startPoint ) {
		pilot = Bukkit.getServer().getPlayer(playerName);
		CraftPilotEvent event = new CraftPilotEvent(this);
		if(event.call())
		AsyncManager.getInstance().submitTask( new DetectionTask( this, startPoint, type.getMinSize(), type.getMaxSize(), type.getAllowedBlocks(), type.getForbiddenBlocks(), playerName, w ), this );
	}

	public void translate( int dx, int dy, int dz ) {
		if(w.getEnvironment() == Environment.THE_END) WarpUtils.translate(this, dx, dy, dz);
		else {
			TranslationTaskData data = new TranslationTaskData( dx, dz, dy, getBlockList(), getHitBox(), minZ, minX, type.getMaxHeightLimit(), type.getMinHeightLimit());
			CraftSyncTranslateEvent event = new CraftSyncTranslateEvent(this, data);
			if(event.call()){
				AsyncManager.getInstance().submitTask( new TranslationTask( this, data ), this );
			}
		}
	}

	public void rotate( Rotation rotation, MovecraftLocation originPoint ) {
		AsyncManager.getInstance().submitTask( new RotationTask( this, originPoint, this.getBlockList(), rotation, this.getW()), this );
	}


	public int getMinX() {
		return minX;
	}

	public void setMinX( int minX ) {
		this.minX = minX;
	}

	public int getMinZ() {
		return minZ;
	}

	public void setMinZ( int minZ ) {
		this.minZ = minZ;
	}
	
	public ShipMoveTask getMoveTask(){
		return moveTask;
	}
	
	public void setMoveTask(ShipMoveTask task){
		moveTask = task;
	}
	public int getVelocity(){
		return velocity;
	}
	public int getSteps(){
		return steps;
	}
	public void setVelocity(int newV){
		velocity = newV;
	}
	public void setSteps(int steps){
		this.steps = steps;
	}
	@SuppressWarnings("deprecation")
	public void extendLandingGear(){
		for (MovecraftLocation l: getBlockList()) {
			Block b = w.getBlockAt(l.getX(), l.getY(), l.getZ());
			if (b.getType() == Material.PISTON_BASE && b.getData() == 0){
				Block above = b.getRelative(BlockFace.UP);
				if (above.getType() == Material.SPONGE){
						Block belowTwo = b.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN);
						if (belowTwo.getType() != Material.AIR){
							above.setTypeIdAndData(152, (byte) 0, true);
						}
				}
			}
		}
	}
	@SuppressWarnings("deprecation")
	public void retractLandingGear(){
		for (MovecraftLocation l : getBlockList()){
			Block b = w.getBlockAt(l.getX(), l.getY(), l.getZ());
			if (b.getType() == Material.PISTON_BASE && b.getData() == 8){
				Block above = b.getRelative(BlockFace.UP);
				if (above.getTypeId() == 152){
					above.setTypeIdAndData(19, (byte) 0, true);
				}
			}
		}
	}
	@SuppressWarnings("deprecation")
	public void shootGuns(Player p){
		
		BlockFace playerFacing = GunUtils.yawToFace(p.getLocation().getYaw());
		int datavalue = GunUtils.getIntegerDirection(playerFacing);
		
		this.processing.set(true);
		for (MovecraftLocation l: getBlockList()){
			
			Block b = w.getBlockAt(l.getX(), l.getY(), l.getZ());
			
			if (b.getType() == Material.PISTON_BASE){
				
				if (b.getData() == datavalue){
					final Block behind = GunUtils.getBlockBehind(b);
					if (behind.getType() == Material.SPONGE){
		
						Block twoinfront = b.getRelative(playerFacing).getRelative(playerFacing);
						
						behind.setType(Material.REDSTONE_BLOCK);
						Fireball f = ((Fireball) twoinfront.getLocation().getWorld().spawnEntity(twoinfront.getLocation(), EntityType.FIREBALL));
						f.setDirection(GunUtils.getFireBallVelocity(playerFacing));
						f.setShooter(pilot);
						twoinfront.getWorld().playSound(twoinfront.getLocation(), Sound.SHOOT_ARROW, 2.0F, 1.0F);
						
						Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Movecraft.getInstance(), new Runnable(){

							@Override
							public void run() {
								behind.setType(Material.SPONGE);
								processing.set(false);
							}
						}, 5L);
					}
				}
			}
		}
	}
}
