package tfar.ae2wtlib.wct;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGridNode;
import appeng.container.ContainerLocator;
import appeng.container.ContainerNull;
import appeng.container.implementations.MEMonitorableContainer;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.CraftingMatrixSlot;
import appeng.container.slot.CraftingTermSlot;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.IContainerCraftingPacket;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.item.crafting.IRecipeType;
import tfar.ae2wtlib.Config;
import tfar.ae2wtlib.terminal.WTInventoryHandler;
import tfar.ae2wtlib.terminal.IWTInvHolder;
import tfar.ae2wtlib.terminal.ae2wtlibInternalInventory;
import tfar.ae2wtlib.util.ContainerHelper;
import tfar.ae2wtlib.wct.magnet_card.ItemMagnetCard;
import tfar.ae2wtlib.wct.magnet_card.MagnetSettings;
import tfar.ae2wtlib.wut.ItemWUT;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

public class WCTContainer extends MEMonitorableContainer implements IAEAppEngInventory, IContainerCraftingPacket, IWTInvHolder {

    public static ContainerType<WCTContainer> TYPE;

    public static final ContainerHelper<WCTContainer, WCTGuiObject> helper = new ContainerHelper<>((int id, PlayerInventory ip, WCTGuiObject gui) -> new WCTContainer(id, ip, gui));

    public static WCTContainer fromNetwork(int windowId, PlayerInventory inv) {
        return helper.fromNetwork(windowId, inv);
    }

    private final AppEngInternalInventory craftingGrid;
    private final CraftingMatrixSlot[] craftingSlots = new CraftingMatrixSlot[9];
    private final CraftingTermSlot outputSlot;
    private IRecipe<CraftingInventory> currentRecipe;
    final WTInventoryHandler WTInventoryHandler;

    public static boolean open(PlayerEntity player, ContainerLocator locator) {
        return helper.open(player, locator);
    }

    private final WCTGuiObject wctGUIObject;

    public WCTContainer(int id, final PlayerInventory ip, final WCTGuiObject gui) {
        super(TYPE, id, ip, gui, true);
        wctGUIObject = gui;

        final int slotIndex = ((IInventorySlotAware) wctGUIObject).getInventorySlot();
        lockPlayerInventorySlot(slotIndex);

        WTInventoryHandler = new WTInventoryHandler(getPlayerInv(), wctGUIObject.getItemStack(), this);
        craftingGrid = new ae2wtlibInternalInventory(this, 9, "crafting", wctGUIObject.getItemStack());
        final IItemHandler crafting = getInventoryByName("crafting");

        for(int y = 0; y < 3; y++) {
            for(int x = 0; x < 3; x++) {
                addSlot(craftingSlots[x + y * 3] = new CraftingMatrixSlot(this, crafting, x + y * 3, 37 + x * 18 + 43, -72 + y * 18 - 4));
            }
        }
        AppEngInternalInventory output = new AppEngInternalInventory(this, 1);
        addSlot(outputSlot = new CraftingTermSlot(getPlayerInv().player, getActionSource(), getPowerSource(), gui.getIStorageGrid(), crafting, crafting, output, 131 + 43, -72 + 18 - 4, this));

        addSlot(new AppEngSlot(WTInventoryHandler, 3, 8, -76) {

            @OnlyIn(Dist.CLIENT)
            public Pair<ResourceLocation, ResourceLocation> getBackground() {
                return Pair.of(PlayerContainer.LOCATION_BLOCKS_TEXTURE, PlayerContainer.EMPTY_ARMOR_SLOT_HELMET);
            }
        });
        addSlot(new AppEngSlot(WTInventoryHandler, 2, 8, -58) {
            @OnlyIn(Dist.CLIENT)
            public Pair<ResourceLocation, ResourceLocation> getBackground() {
                return Pair.of(PlayerContainer.LOCATION_BLOCKS_TEXTURE, PlayerContainer.EMPTY_ARMOR_SLOT_CHESTPLATE);
            }
        });
        addSlot(new AppEngSlot(WTInventoryHandler, 1, 8, -40) {
            @OnlyIn(Dist.CLIENT)
            public Pair<ResourceLocation, ResourceLocation> getBackground() {
                return Pair.of(PlayerContainer.LOCATION_BLOCKS_TEXTURE, PlayerContainer.EMPTY_ARMOR_SLOT_LEGGINGS);
            }
        });
        addSlot(new AppEngSlot(WTInventoryHandler, 0, 8, -22) {
            @OnlyIn(Dist.CLIENT)
            public Pair<ResourceLocation, ResourceLocation> getBackground() {
                return Pair.of(PlayerContainer.LOCATION_BLOCKS_TEXTURE, PlayerContainer.EMPTY_ARMOR_SLOT_BOOTS);
            }
        });

        addSlot(new AppEngSlot(WTInventoryHandler, tfar.ae2wtlib.terminal.WTInventoryHandler.OFFHAND, 80, -22) {
            @OnlyIn(Dist.CLIENT)
            public Pair<ResourceLocation, ResourceLocation> getBackground() {
                return Pair.of(PlayerContainer.LOCATION_BLOCKS_TEXTURE, PlayerContainer.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });
        addSlot(new AppEngSlot(WTInventoryHandler, tfar.ae2wtlib.terminal.WTInventoryHandler.TRASH, 98, -22));
        addSlot(new AppEngSlot(WTInventoryHandler, tfar.ae2wtlib.terminal.WTInventoryHandler.INFINITY_BOOSTER_CARD, 134, -20));
        addSlot(new AppEngSlot(WTInventoryHandler, tfar.ae2wtlib.terminal.WTInventoryHandler.MAGNET_CARD, 152, -20));//TODO fetch texture for card background
    }

    private int ticks = 0;

    @Override
    public void detectAndSendChanges() {
        if(isClient()) return;
        super.detectAndSendChanges();

        if(!wctGUIObject.rangeCheck()) {
            if(isValidContainer()) {
                getPlayerInv().player.sendMessage(PlayerMessages.OutOfRange.get(), Util.DUMMY_UUID);
                ((ServerPlayerEntity) getPlayerInv().player).closeContainer();
            }
            setValidContainer(false);
        } else {
            double powerMultiplier = Config.getPowerMultiplier(wctGUIObject.getRange(), wctGUIObject.isOutOfRange());
            ticks++;
            if(ticks > 10) {
                wctGUIObject.extractAEPower((powerMultiplier) * ticks, Actionable.MODULATE, PowerMultiplier.CONFIG);
                ticks = 0;
            }

            if(wctGUIObject.extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.ONE) == 0) {
                if(isValidContainer()) {
                    getPlayerInv().player.sendMessage(PlayerMessages.DeviceNotPowered.get(), Util.DUMMY_UUID);
                    ((ServerPlayerEntity) getPlayerInv().player).closeContainer();
                }
                setValidContainer(false);
            }
        }
    }

    /**
     * Callback for when the crafting matrix is changed.
     */

    @Override
    public void onCraftMatrixChanged(IInventory inventory) {
        final ContainerNull cn = new ContainerNull();
        final CraftingInventory ic = new CraftingInventory(cn, 3, 3);

        for(int x = 0; x < 9; x++) {
            ic.setInventorySlotContents(x, craftingSlots[x].getStack());
        }

        if(currentRecipe == null || !currentRecipe.matches(ic, this.getPlayerInv().player.world)) {
            World world = this.getPlayerInv().player.world;
            currentRecipe = world.getRecipeManager().getRecipe(IRecipeType.CRAFTING, ic, world).orElse(null);
        }

        if(currentRecipe == null) {
            outputSlot.putStack(ItemStack.EMPTY);
        } else {
            final ItemStack craftingResult = currentRecipe.getCraftingResult(ic);
            outputSlot.putStack(craftingResult);
        }
    }

    //todo, support things outside of mainhand
    @Override
    public boolean canInteractWith(PlayerEntity player) {
        return wctGUIObject.getItemStack() == player.getHeldItemMainhand();
    }

    @Override
    public void saveChanges() {}

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {}

    @Override
    public IItemHandler getInventoryByName(String name) {
        if(name.equals("player")) {
            return new InvWrapper(getPlayerInventory());
        } else if(name.equals("crafting")) {
            return craftingGrid;
        }
        return null;
    }

    @Override
    public IGridNode getNetworkNode() {
        return wctGUIObject.getActionableNode();
    }

    @Override
    public boolean useRealItems() {
        return true;
    }

    public void deleteTrashSlot() {
        WTInventoryHandler.setStackInSlot(tfar.ae2wtlib.terminal.WTInventoryHandler.TRASH, ItemStack.EMPTY);
    }

    private MagnetSettings magnetSettings;

    public MagnetSettings getMagnetSettings() {
        if(magnetSettings == null) return reloadMagnetSettings();
        return magnetSettings;
    }

    public void saveMagnetSettings() {
        ItemMagnetCard.saveMagnetSettings(wctGUIObject.getItemStack(), magnetSettings);
    }

    public MagnetSettings reloadMagnetSettings() {
        magnetSettings = ItemMagnetCard.loadMagnetSettings(wctGUIObject.getItemStack());
        if(isClient() && screen != null) screen.resetMagnetSettings();
        return magnetSettings;
    }

    private WCTScreen screen;

    public void setScreen(WCTScreen screen) {
        this.screen = screen;
    }

    public boolean isWUT() {
        return wctGUIObject.getItemStack().getItem() instanceof ItemWUT;
    }

    @Override
    public ItemStack[] getViewCells() {
        return wctGUIObject.getViewCellStorage().getViewCells();
    }
}