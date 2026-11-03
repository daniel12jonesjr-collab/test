package net.runelite.client.plugins.microbot.highalcher.market.ui;

import net.runelite.client.plugins.microbot.highalcher.NoticeCenter;

import java.util.List;

/** What the side panel needs from the bot: start/stop, pause/resume and the collected notices. */
public interface BotControls
{
	void toggleRunning();

	boolean isRunning();

	void togglePause();

	boolean isPaused();

	List<NoticeCenter.Notice> notices();

	void clearNotices();

	/** Types the name into the GE item search if it is open. False when it is not. */
	boolean typeIntoSearch(String name);
}
