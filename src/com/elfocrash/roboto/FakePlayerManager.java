package com.elfocrash.roboto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.elfocrash.roboto.ai.GhostHunterAI;
import com.elfocrash.roboto.ai.CardinalAI;
import com.elfocrash.roboto.ai.TitanAI;
import com.elfocrash.roboto.ai.FakePlayerAI;
import com.elfocrash.roboto.ai.FallbackAI;
import com.elfocrash.roboto.ai.SaggitariusAI;
import com.elfocrash.roboto.ai.SoultakerAI;
import com.elfocrash.roboto.ai.DominatorAI;
import com.elfocrash.roboto.ai.DuelistAI;
import com.elfocrash.roboto.ai.GhostSentinelAI;
import com.elfocrash.roboto.ai.GrandKhavatariAI;
import com.elfocrash.roboto.ai.WindRiderAI;
import com.elfocrash.roboto.ai.MoonlightSentinelAI;
import com.elfocrash.roboto.ai.ArchmageAI;
import com.elfocrash.roboto.ai.StormScreamerAI;
import com.elfocrash.roboto.ai.MysticMuseAI;
import com.elfocrash.roboto.ai.AdventurerAI;

import net.sf.l2j.Config;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.data.sql.PlayerInfoTable;
import net.sf.l2j.gameserver.data.xml.MapRegionData.TeleportType;
import net.sf.l2j.gameserver.data.xml.PlayerData;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.instancemanager.CastleManager;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.appearance.PcAppearance;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.actor.instance.Player;
import net.sf.l2j.gameserver.model.actor.template.PlayerTemplate;
import net.sf.l2j.gameserver.model.base.ClassId;
import net.sf.l2j.gameserver.model.base.ClassRace;
import net.sf.l2j.gameserver.model.base.Experience;
import net.sf.l2j.gameserver.model.base.Sex;
import net.sf.l2j.gameserver.model.entity.Castle;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.model.entity.Siege.SiegeSide;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

/**
 * @author Elfocrash
 *
 */
public enum FakePlayerManager {
	INSTANCE;

	private FakePlayerManager() {

	}

	public Class<? extends Creature> getTestTargetClass() {
		return Monster.class;
	}

	public int getTestTargetRange() {
		return 2000;
	}

	public void initialise() {
		FakePlayerNameManager.INSTANCE.initialise();
		FakePlayerTaskManager.INSTANCE.initialise();
	}

	public FakePlayer spawnPlayer(int x, int y, int z) {
		FakePlayer activeChar = createRandomFakePlayer();
		World.getInstance().addPlayer(activeChar);
		handlePlayerClanOnSpawn(activeChar);
		
		if (Config.PLAYER_SPAWN_PROTECTION > 0)
			activeChar.setSpawnProtection(true);
		
		activeChar.spawnMe(x, y, z);
		activeChar.onPlayerEnter();
		
		if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() < 2)
				&& activeChar.isInsideZone(ZoneId.SIEGE))
			activeChar.teleToLocation(TeleportType.TOWN);

		activeChar.heal();
		return activeChar;
	}

	public void despawnFakePlayer(int objectId) {
		Player player = World.getInstance().getPlayer(objectId);
		if (player instanceof FakePlayer) {
			FakePlayer fakePlayer = (FakePlayer) player;
			fakePlayer.despawnPlayer();
		}
	}

	private static void handlePlayerClanOnSpawn(FakePlayer activeChar) {
		final Clan clan = activeChar.getClan();
		if (clan != null) {
			clan.getClanMember(activeChar.getObjectId()).setPlayerInstance(activeChar);

			final SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.CLAN_MEMBER_S1_LOGGED_IN)
					.addCharName(activeChar);
			final PledgeShowMemberListUpdate update = new PledgeShowMemberListUpdate(activeChar);

			// Send packets to others members.
			for (Player member : clan.getOnlineMembers()) {
				if (member == activeChar)
					continue;

				member.sendPacket(msg);
				member.sendPacket(update);
			}

			for (Castle castle : CastleManager.getInstance().getCastles()) {
				final Siege siege = castle.getSiege();
				if (!siege.isInProgress())
					continue;

				final SiegeSide type = siege.getSide(clan);
				if (type == SiegeSide.ATTACKER)
					activeChar.setSiegeState((byte) 1);
				else if (type == SiegeSide.DEFENDER || type == SiegeSide.OWNER)
					activeChar.setSiegeState((byte) 2);
			}
		}
	}

	public FakePlayer createRandomFakePlayer() {
		int objectId = IdFactory.getInstance().getNextId();
		String accountName = "AutoPilot";
		String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();

		ClassId classId = getThirdClasses().get(Rnd.get(0, getThirdClasses().size() - 1));

		final PlayerTemplate template = PlayerData.getInstance().getTemplate(classId);
		PcAppearance app = getRandomAppearance(template.getRace());
		FakePlayer player = new FakePlayer(objectId, template, accountName, app);

		player.setName(playerName);
		player.setAccessLevel(Config.DEFAULT_ACCESS_LEVEL);
		PlayerInfoTable.getInstance().addPlayer(objectId, accountName, playerName, player.getAccessLevel().getLevel());
		player.setBaseClass(player.getClassId());
		setLevel(player, 81);
		player.rewardSkills();

		giveArmorsByClass(player);
		giveWeaponsByClass(player,true);
		player.heal();

		return player;
	}

	public void giveArmorsByClass(FakePlayer player) {
		List<Integer> itemIds = new ArrayList<>();
		switch (player.getClassId()) {
		case ARCHMAGE:
		case SOULTAKER:
		case HIEROPHANT:
		case ARCANA_LORD:
		case CARDINAL:
		case MYSTIC_MUSE:
		case ELEMENTAL_MASTER:
		case EVAS_SAINT:
		case STORM_SCREAMER:
		case SPECTRAL_MASTER:
		case SHILLIEN_SAINT:
		case DOMINATOR:
		case DOOMCRYER:
			itemIds = Arrays.asList(2407, 512, 5767, 5779, 858, 858, 889, 889, 920);
			break;
		case DUELIST:
		case DREADNOUGHT:
		case PHOENIX_KNIGHT:
		case SWORD_MUSE:
		case HELL_KNIGHT:
		case SPECTRAL_DANCER:
		case EVAS_TEMPLAR:
		case SHILLIEN_TEMPLAR:
		case TITAN:
		case MAESTRO:
			itemIds = Arrays.asList(6373, 6374, 6375, 6376, 6378, 858, 858, 889, 889, 920);
			break;
		case SAGGITARIUS:
		case ADVENTURER:
		case WIND_RIDER:
		case MOONLIGHT_SENTINEL:
		case GHOST_HUNTER:
		case GHOST_SENTINEL:
		case FORTUNE_SEEKER:
		case GRAND_KHAVATARI:
			itemIds = Arrays.asList(6379, 6380, 6381, 6382, 858, 858, 889, 889, 920);
			break;
		default:
			break;
		}
		for (int id : itemIds) {
			player.getInventory().addItem("Armors", id, 1, player, null);
			ItemInstance item = player.getInventory().getItemByItemId(id);
			// enchant the item??
			player.getInventory().equipItemAndRecord(item);
			player.getInventory().reloadEquippedItems();
			player.broadcastCharInfo();
		}
	}

	public void giveWeaponsByClass(FakePlayer player, boolean randomlyEnchant) {
		List<Integer> itemIds = new ArrayList<>();
		switch (player.getClassId()) {
		case FORTUNE_SEEKER:
		case GHOST_HUNTER:
		case WIND_RIDER:
		case ADVENTURER:
			itemIds = Arrays.asList(6590);
			break;
		case SAGGITARIUS:
		case MOONLIGHT_SENTINEL:
		case GHOST_SENTINEL:
			itemIds = Arrays.asList(7577);
			break;
		case PHOENIX_KNIGHT:
		case SWORD_MUSE:
		case HELL_KNIGHT:
		case EVAS_TEMPLAR:
		case SHILLIEN_TEMPLAR:
			itemIds = Arrays.asList(6583, 6377);
			break;
		case MAESTRO:
			itemIds = Arrays.asList(6585, 6377);
			break;
		case TITAN:
			itemIds = Arrays.asList(6607);
			break;
		case DUELIST:
		case SPECTRAL_DANCER:
			itemIds = Arrays.asList(6580);
			break;
		case DREADNOUGHT:
			itemIds = Arrays.asList(6599);
			break;
		case ARCHMAGE:
		case SOULTAKER:
		case HIEROPHANT:
		case ARCANA_LORD:
		case CARDINAL:
		case MYSTIC_MUSE:
		case ELEMENTAL_MASTER:
		case EVAS_SAINT:
		case STORM_SCREAMER:
		case SPECTRAL_MASTER:
		case SHILLIEN_SAINT:
		case DOMINATOR:
		case DOOMCRYER:
			itemIds = Arrays.asList(6608);
			break;
		case GRAND_KHAVATARI:
			itemIds = Arrays.asList(6602);
			break;
		default:
			break;
		}
		for (int id : itemIds) {
			player.getInventory().addItem("Weapon", id, 1, player, null);
			ItemInstance item = player.getInventory().getItemByItemId(id);
			if(randomlyEnchant)
				item.setEnchantLevel(Rnd.get(7, 20));
			player.getInventory().equipItemAndRecord(item);
			player.getInventory().reloadEquippedItems();
		}
	}

	public List<ClassId> getThirdClasses() {
		// removed summoner classes because fuck those guys
		List<ClassId> classes = new ArrayList<>();

		/*
		 * classes.add(ClassId.EVAS_SAINT); classes.add(ClassId.SHILLIEN_TEMPLAR);
		 * classes.add(ClassId.SPECTRAL_DANCER); classes.add(ClassId.GHOST_HUNTER);
		 * 
		 * classes.add(ClassId.DREADNOUGHT); classes.add(ClassId.PHOENIX_KNIGHT);
		 * classes.add(ClassId.HELL_KNIGHT);
		 * 
		 * classes.add(ClassId.HIEROPHANT); classes.add(ClassId.EVAS_TEMPLAR);
		 * classes.add(ClassId.SWORD_MUSE);
		 * 
		 * classes.add(ClassId.DOOMCRYER); classes.add(ClassId.FORTUNE_SEEKER);
		 * classes.add(ClassId.MAESTRO);
		 */

		// classes.add(ClassId.ARCANA_LORD);
		// classes.add(ClassId.ELEMENTAL_MASTER);
		// classes.add(ClassId.SPECTRAL_MASTER);
		// classes.add(ClassId.SHILLIEN_SAINT);

		classes.add(ClassId.SAGGITARIUS);
		classes.add(ClassId.ARCHMAGE);
		classes.add(ClassId.SOULTAKER);
		classes.add(ClassId.MYSTIC_MUSE);
		classes.add(ClassId.STORM_SCREAMER);
		classes.add(ClassId.MOONLIGHT_SENTINEL);
		classes.add(ClassId.GHOST_SENTINEL);
		classes.add(ClassId.ADVENTURER);
		classes.add(ClassId.WIND_RIDER);
		classes.add(ClassId.DOMINATOR);
		classes.add(ClassId.TITAN);
		classes.add(ClassId.CARDINAL);
		classes.add(ClassId.DUELIST);

		classes.add(ClassId.GRAND_KHAVATARI);

		return classes;
	}

	public Map<ClassId, Class<? extends FakePlayerAI>> getAllAIs() {
		Map<ClassId, Class<? extends FakePlayerAI>> ais = new HashMap<>();
		ais.put(ClassId.STORM_SCREAMER, StormScreamerAI.class);
		ais.put(ClassId.MYSTIC_MUSE, MysticMuseAI.class);
		ais.put(ClassId.ARCHMAGE, ArchmageAI.class);
		ais.put(ClassId.SOULTAKER, SoultakerAI.class);
		ais.put(ClassId.SAGGITARIUS, SaggitariusAI.class);
		ais.put(ClassId.MOONLIGHT_SENTINEL, MoonlightSentinelAI.class);
		ais.put(ClassId.GHOST_SENTINEL, GhostSentinelAI.class);
		ais.put(ClassId.ADVENTURER, AdventurerAI.class);
		ais.put(ClassId.WIND_RIDER, WindRiderAI.class);
		ais.put(ClassId.GHOST_HUNTER, GhostHunterAI.class);
		ais.put(ClassId.DOMINATOR, DominatorAI.class);
		ais.put(ClassId.TITAN, TitanAI.class);
		ais.put(ClassId.CARDINAL, CardinalAI.class);
		ais.put(ClassId.DUELIST, DuelistAI.class);

		ais.put(ClassId.GRAND_KHAVATARI, GrandKhavatariAI.class);
		return ais;
	}

	public PcAppearance getRandomAppearance(ClassRace race) {

		Sex randomSex = Rnd.get(1, 2) == 1 ? Sex.MALE : Sex.FEMALE;
		int hairStyle = Rnd.get(0, randomSex == Sex.MALE ? 4 : 6);
		int hairColor = Rnd.get(0, 3);
		int faceId = Rnd.get(0, 2);

		return new PcAppearance((byte) faceId, (byte) hairColor, (byte) hairStyle, randomSex);
	}

	public void setLevel(FakePlayer player, int level) {
		if (level >= 1 && level <= Experience.MAX_LEVEL) {
			long pXp = player.getExp();
			long tXp = Experience.LEVEL[81];

			if (pXp > tXp)
				player.removeExpAndSp(pXp - tXp, 0);
			else if (pXp < tXp)
				player.addExpAndSp(tXp - pXp, 0);
		}
	}

	public Class<? extends FakePlayerAI> getAIbyClassId(ClassId classId) {
		Class<? extends FakePlayerAI> ai = getAllAIs().get(classId);
		if (ai == null)
			return FallbackAI.class;

		return ai;
	}

	public int getFakePlayersCount() {
		return getFakePlayers().size();
	}

	public List<FakePlayer> getFakePlayers() {
		return World.getInstance().getPlayers().stream().filter(x -> x instanceof FakePlayer).map(x -> (FakePlayer) x)
				.collect(Collectors.toList());
	}

	public int[][] getFighterBuffs() {
		return new int[][] { { 1204, 2 }, // wind walk	                
            { 1040, 3 }, // shield
			{ 1035, 4 }, // Mental Shield
			{ 1045, 6 }, // Bless the Body				
			{ 1068, 3 }, // might
			{ 1062, 2 }, // besekers
			{ 1086, 2 }, // haste
			{ 1077, 3 }, // focus
			{ 1388, 3 }, // Greater Might
			{ 1036, 2 }, // magic barrier
			{ 274, 1 }, // dance of fire
			{ 273, 1 }, // dance of fury
			{ 268, 1 }, // dance of wind
			{ 271, 1 }, // dance of warrior
			{ 267, 1 }, // Song of Warding
			{ 349, 1 }, // Song of Renewal
			{ 264, 1 }, // song of earth
			{ 269, 1 }, // song of hunter
			{ 364, 1 }, // song of champion
			{ 1363, 1 }, // chant of victory
			{ 4699, 5 } // Blessing of Queen
		};
	}

	public int[][] getMageBuffs() {
		return new int[][] { { 1204, 2 }, // wind walk	
            { 1040, 3 }, // shield
			{ 1035, 4 }, // Mental Shield
			{ 4351, 6 }, // Concentration
			{ 1036, 2 }, // Magic Barrier
			{ 1045, 6 }, // Bless the Body
			{ 1303, 2 }, // Wild Magic
			{ 1085, 3 }, // acumen
			{ 1062, 2 }, // besekers
			{ 1059, 3 }, // empower
			{ 1389, 3 }, // Greater Shield
			{ 273, 1 }, // dance of the mystic
			{ 276, 1 }, // dance of concentration
			{ 365, 1 }, // Dance of Siren
			{ 264, 1 }, // song of earth
			{ 268, 1 }, // song of wind
			{ 267, 1 }, // Song of Warding
			{ 349, 1 }, // Song of Renewal
			{ 1413, 1 }, // Magnus\' Chant
			{ 4703, 4 } // Gift of Seraphim
		};
	}
}
