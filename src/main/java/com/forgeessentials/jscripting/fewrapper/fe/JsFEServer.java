package com.forgeessentials.jscripting.fewrapper.fe;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

import javax.script.ScriptException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IInteractionObject;

import com.forgeessentials.api.APIRegistry;
import com.forgeessentials.api.UserIdent;
import com.forgeessentials.jscripting.ScriptInstance;
import com.forgeessentials.jscripting.command.CommandJScriptCommand;
import com.forgeessentials.jscripting.wrapper.mc.JsICommandSender;
import com.forgeessentials.jscripting.wrapper.mc.entity.JsEntityPlayer;
import com.forgeessentials.jscripting.wrapper.mc.item.JsInteractionObject;
import com.forgeessentials.jscripting.wrapper.mc.item.JsInventory;
import com.forgeessentials.jscripting.wrapper.mc.item.JsItemStack;
import com.forgeessentials.util.PlayerInfo;

/**
 * @tsd.interface FEServer
 */
public class JsFEServer
{

    private ScriptInstance script;

    private JsICommandSender server;

    public JsFEServer(ScriptInstance script)
    {
        this.script = script;
    }

    /**
     * Registers a new command in the game. <br>
     * The processCommand and tabComplete handler can be the same, if the processCommand handler properly checks for args.isTabCompletion.
     *
     * @tsd.def registerCommand(options: CommandOptions): void;
     */
    public void registerCommand(Object options) throws ScriptException
    {
        JsCommandOptions opt = script.getProperties(new JsCommandOptions(), options, JsCommandOptions.class);
        script.registerScriptCommand(new CommandJScriptCommand(script, opt));
    }

    /**
     * Returns the total number of unique players that have connected to this server
     */
    public int getUniquePlayerCount()
    {
        return APIRegistry.perms.getServerZone().getKnownPlayers().size();
    }
    /**
     * Returns the list of players who have ever connected.
     */
    public Set<UserIdent> getAllPlayers()
    {
        return APIRegistry.perms.getServerZone().getKnownPlayers();
    }
    /**
     * Returns the amount of time this player was active on the server in seconds
     */
    public long getTimePlayed(UUID playerId)
    {
        PlayerInfo pi = PlayerInfo.get(playerId);
        return pi == null ? 0 : pi.getTimePlayed() / 1000;
    }

    public Date getLastLogout(UUID playerId)
    {
        PlayerInfo pi = PlayerInfo.get(playerId);
        return pi == null ? null : pi.getLastLogout();
    }

    public Date getLastLogin(UUID playerId)
    {
        PlayerInfo pi = PlayerInfo.get(playerId);
        return pi == null ? null : pi.getLastLogin();
    }

    public JsInventory<InventoryBasic> createCustomInventory(final String name, boolean hasCustom, JsItemStack[] stacks)
    {
        InventoryBasic inventoryBasic = new InventoryBasic(name, hasCustom, stacks.length);
        for (int i = 0; i < stacks.length; i++)
        {
            inventoryBasic.setInventorySlotContents(i, stacks[i].getThat());
        }
        return JsInventory.get(inventoryBasic);
    }

    ;

    public JsInventory<InventoryBasic> cloneInventory(final String name, boolean hasCustom, JsInventory<IInventory> inventory, int size)
    {
        if (size > inventory.getSize())
        {
            size = inventory.getSize();
        }

        InventoryBasic inventoryBasic = new InventoryBasic(name, hasCustom, size);
        for (int i = 0; i < size; i++)
        {
            inventoryBasic.setInventorySlotContents(i, inventory.getThat().getStackInSlot(i));

        }
        return JsInventory.get(inventoryBasic);
    }

    private abstract class BasicInteraction extends InventoryBasic implements IInteractionObject
    {

        public BasicInteraction(String p_i1561_1_, boolean p_i1561_2_, IInventory source)
        {
            super(p_i1561_1_, p_i1561_2_, ((source.getSizeInventory() - 1) / 9 + 1) * 9);
            for (int i = 0; i < source.getSizeInventory(); i++)
            {
                this.setInventorySlotContents(i, source.getStackInSlot(i));
            }
        }
    }

    public JsInteractionObject<IInteractionObject> getMenuChest(final String name, final String displayName, final JsInventory<IInventory> inventory, final String callbackMethod)
    {
        final boolean hasCustomName = displayName != null;

        final IInteractionObject menuChest = new BasicInteraction(name, hasCustomName, inventory.getThat())
        {
            @Override public Container createContainer(InventoryPlayer inventoryPlayer, EntityPlayer entityPlayer)
            {
                return new ContainerChest(inventoryPlayer, this, entityPlayer)
                {
                    @Override public ItemStack slotClick(int p_slotClick_1_, int p_slotClick_2_, ClickType p_slotClick_3_, EntityPlayer p_slotClick_4_)
                    {
                        JsItemStack stack = JsItemStack.EMPTY;
                        try
                        {
                            Object _stack = script.tryCallGlobal(callbackMethod, JsEntityPlayer.get(p_slotClick_4_), p_slotClick_1_, p_slotClick_2_, p_slotClick_3_);
                            if (_stack instanceof JsItemStack)
                            {
                                stack = (JsItemStack) _stack;
                            }
                        }
                        catch (ScriptException e)
                        {
                            e.printStackTrace();
                        }

                        return stack.getThat();
                    }

                    @Override public ItemStack transferStackInSlot(EntityPlayer p_transferStackInSlot_1_, int p_transferStackInSlot_2_)
                    {
                        return ItemStack.EMPTY;
                    }

                    @Override public boolean canMergeSlot(ItemStack p_canMergeSlot_1_, Slot p_canMergeSlot_2_)
                    {
                        return false;
                    }

                    @Override public boolean canDragIntoSlot(Slot p_canDragIntoSlot_1_)
                    {
                        return false;
                    }
                };
            }

            @Override public String getGuiID()
            {
                return "minecraft:chest";
            }

            @Override public String getName()
            {
                return name;
            }

            @Override public boolean hasCustomName()
            {
                return hasCustomName;
            }

            @Override public ITextComponent getDisplayName()
            {
                return displayName != null ? new TextComponentString(displayName) : null;
            }
        };

        return new JsInteractionObject<>(menuChest);
    }
}
