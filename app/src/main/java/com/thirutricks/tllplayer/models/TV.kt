package com.thirutricks.tllplayer.models

import java.io.Serializable

data class TV(
    var id: Int = 0,
    var name: String = "",
    var title: String = "",
    var description: String? = null,
    var logo: String = "",
    var image: String? = null,
    var uris: List<String>,
    var headers: Map<String, String>? = null,
    var group: String = "",
    var type: Type = Type.WEB,
    var child: List<TV>,
) : Serializable {

    override fun toString(): String {
        return "TV{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", logo='" + logo + '\'' +
                ", image='" + image + '\'' +
                ", uris='" + uris + '\'' +
                ", headers='" + headers + '\'' +
                ", group='" + group + '\'' +
                ", type='" + type + '\'' +
                '}'
    }
}