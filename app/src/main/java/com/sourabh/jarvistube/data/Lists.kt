package com.sourabh.jarvistube.data

/** One from each list in turn, without repeats, so no list dominates. */
fun interleave(lists: Collection<List<VideoItem>>): List<VideoItem> {
    val mixed = ArrayList<VideoItem>()
    for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) lists.forEach { l -> l.getOrNull(i)?.let(mixed::add) }
    return mixed.distinctBy { it.url }
}
