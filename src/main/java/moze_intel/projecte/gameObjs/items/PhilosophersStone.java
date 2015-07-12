package moze_intel.projecte.gameObjs.items;

import com.google.common.collect.Sets;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.IExtraFunction;
import moze_intel.projecte.api.IProjectileShooter;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.blocks.BlockDirection;
import moze_intel.projecte.gameObjs.entity.EntityMobRandomizer;
import moze_intel.projecte.gameObjs.tiles.TileEmc;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.ParticlePKT;
import moze_intel.projecte.utils.AchievementHandler;
import moze_intel.projecte.utils.ClientKeyHelper;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PEKeybind;
import moze_intel.projecte.utils.PlayerHelper;
import moze_intel.projecte.utils.WorldHelper;
import moze_intel.projecte.utils.WorldTransmutations;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;
import java.util.Set;

public class PhilosophersStone extends ItemMode implements IProjectileShooter, IExtraFunction
{
	public PhilosophersStone()
	{
		super("philosophers_stone", (byte)4, new String[] {
				StatCollector.translateToLocal("pe.philstone.mode1"),
				StatCollector.translateToLocal("pe.philstone.mode2"),
				StatCollector.translateToLocal("pe.philstone.mode3")});
		this.setContainerItem(this);
		this.setNoRepair();
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing sideHit, float px, float py, float pz)
	{
		if (world.isRemote)
		{
			return false;
		}

		IBlockState state = world.getBlockState(pos);

		if (!world.isAirBlock(pos))
		{
			TileEntity tile = world.getTileEntity(pos);
			
			if (player.isSneaking())
			{
				if (tile instanceof TileEmc)
				{
					NBTTagCompound nbt = new NBTTagCompound();
					nbt.setBoolean("ProjectEBlock", true);
					tile.writeToNBT(nbt);
					
					ItemStack s;
					if (state.getBlock() == ObjHandler.dmFurnaceOn)
					{
						s = new ItemStack(ObjHandler.dmFurnaceOff);
					}
					else if (state.getBlock() == ObjHandler.rmFurnaceOn)
					{
						s = new ItemStack(ObjHandler.rmFurnaceOff);
					}
					else if (state.getBlock() instanceof BlockDirection)
					{
						s = ItemHelper.stateToStack(state.withProperty(BlockDirection.FACING, EnumFacing.SOUTH), 1);
					}
					else
					{
						s = ItemHelper.stateToStack(state, 1);
					}
					
					s.setTagCompound(nbt);
					
					world.removeTileEntity(pos);
					world.setBlockToAir(pos);
					WorldHelper.spawnEntityItem(world, s, pos.getX(), pos.getY(), pos.getZ());
				}
			}
		}

		IBlockState result = WorldTransmutations.getWorldTransmutation(world, pos, player.isSneaking());

		if (result != null)
		{
			int mode = this.getMode(stack);
			int charge = this.getCharge(stack);

			for (BlockPos currentPos : getAffectedPositions(world, pos, player, sideHit, mode, charge))
			{
				world.setBlockState(currentPos, result);
				if (world.rand.nextInt(8) == 0)
				{
					PacketHandler.sendToAllAround(new ParticlePKT(EnumParticleTypes.SMOKE_LARGE, pos.getX(), pos.getY() + 1, pos.getZ()), new TargetPoint(world.provider.getDimensionId(), pos.getX(), pos.getY() + 1, pos.getZ(), 32));
				}
			}

			world.playSoundAtEntity(player, "projecte:item.petransmute", 1.0F, 1.0F);

			PlayerHelper.swingItem(player);
		}
		
		return true;
	}
	
	@Override
	public void onCreated(ItemStack stack, World world, EntityPlayer player) 
	{
		super.onCreated(stack, world, player);
		
		if (!world.isRemote)
		{
			player.addStat(AchievementHandler.PHIL_STONE, 1);
		}
	}
	
	@Override
	public boolean shootProjectile(EntityPlayer player, ItemStack stack) 
	{
		World world = player.worldObj;
		world.playSoundAtEntity(player, "projecte:item.petransmute", 1.0F, 1.0F);
		world.spawnEntityInWorld(new EntityMobRandomizer(world, player));
		return true;
	}
	
	@Override
	public void doExtraFunction(ItemStack stack, EntityPlayer player) 
	{
		if (!player.worldObj.isRemote)
		{
			player.openGui(PECore.instance, Constants.PHILOS_STONE_GUI, player.worldObj, (int) player.posX, (int) player.posY, (int) player.posZ);
		}
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean par4) 
	{
		list.add(String.format(StatCollector.translateToLocal("pe.philstone.tooltip1"), ClientKeyHelper.getKeyName(PEKeybind.EXTRA_FUNCTION)));
		list.add(StatCollector.translateToLocal("pe.philstone.tooltip2"));
		list.add(StatCollector.translateToLocal("pe.philstone.tooltip3"));
		list.add(StatCollector.translateToLocal("pe.philstone.tooltip4"));
	}

	public static Set<BlockPos> getAffectedPositions(World world, BlockPos pos, EntityPlayer player, EnumFacing sideHit, int mode, int charge)
	{
		Set<BlockPos> ret = Sets.newHashSet();
		IBlockState targeted = world.getBlockState(pos);
		Iterable<BlockPos> iterable = null;

		switch (mode)
		{
			case 0: // Cube
				iterable = WorldHelper.getPositionsFromCorners(pos.add(-charge, -charge, -charge), pos.add(charge, charge, charge));
				break;
			case 1: // Panel
				if (sideHit == EnumFacing.UP || sideHit == EnumFacing.DOWN)
				{
					iterable = WorldHelper.getPositionsFromCorners(pos.add(-charge, 0, -charge), pos.add(charge, 0, charge));
				}
				else if (sideHit == EnumFacing.EAST || sideHit == EnumFacing.WEST)
				{
					iterable = WorldHelper.getPositionsFromCorners(pos.add(0, -charge, -charge), pos.add(0, charge, charge));
				}
				else if (sideHit == EnumFacing.SOUTH || sideHit == EnumFacing.NORTH)
				{
					iterable = WorldHelper.getPositionsFromCorners(pos.add(-charge, -charge, 0), pos.add(charge, charge, 0));
				}
				break;
			case 2: // Line
				EnumFacing playerFacing = player.getHorizontalFacing();

				if (playerFacing.getAxis() == EnumFacing.Axis.Z)
				{
					iterable = WorldHelper.getPositionsFromCorners(pos.add(0, 0, -charge), pos.add(0, 0, charge));
				}
				else if (playerFacing.getAxis() == EnumFacing.Axis.X)
				{
					iterable = WorldHelper.getPositionsFromCorners(pos.add(-charge, 0, 0), pos.add(charge, 0, 0));
				}
				break;
		}

		if (iterable != null) {
			for (BlockPos currentPos : iterable)
            {
                if (world.getBlockState(currentPos) == targeted)
                {
                    ret.add(currentPos);
                }
            }
		}

		return ret;
	}
}
