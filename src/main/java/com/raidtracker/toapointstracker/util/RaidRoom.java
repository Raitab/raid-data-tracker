/*
BSD 2-Clause License

Copyright (c) 2022, LlemonDuck
Copyright (c) 2022, TheStonedTurtle
Copyright (c) 2019, Ron Young <https://github.com/raiyni>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.raidtracker.toapointstracker.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RaidRoom
{

	NEXUS(new int[]{14160}, "Nexus", RaidRoomType.LOBBY),
	CRONDIS(new int[]{15698}, "Crondis", RaidRoomType.PUZZLE),
	ZEBAK(new int[]{15700}, "Zebak", RaidRoomType.BOSS),
	SCABARAS(new int[]{14162}, "Scabaras", RaidRoomType.PUZZLE),
	KEPHRI(new int[]{14164}, "Kephri", RaidRoomType.BOSS),
	APMEKEN(new int[]{15186}, "Apmeken", RaidRoomType.PUZZLE),
	BABA(new int[]{15188}, "Ba-Ba", RaidRoomType.BOSS),
	HET(new int[]{14674}, "Het", RaidRoomType.PUZZLE),
	AKKHA(new int[]{14676}, "Akkha", RaidRoomType.BOSS),
	WARDENS(new int[]{15184, 15696}, "Wardens", RaidRoomType.BOSS),
	TOMB(new int[]{14672}, "Tomb", RaidRoomType.LOBBY);

	public enum RaidRoomType
	{
		LOBBY,
		PUZZLE,
		BOSS;
	}

	private final int[] regionIds;

	@Getter
	private final String displayName;

	@Getter
	private final RaidRoomType roomType;

	public static RaidRoom forRegionId(int region)
	{
		for (RaidRoom r : RaidRoom.values())
		{
			for (int regionId : r.regionIds)
			{
				if (regionId == region)
				{
					return r;
				}
			}
		}

		return null;
	}

	public static RaidRoom forString(String roomName)
	{
		for (RaidRoom r : RaidRoom.values())
		{
			if (r.getDisplayName().equals(roomName))
			{
				return r;
			}
		}

		return null;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
