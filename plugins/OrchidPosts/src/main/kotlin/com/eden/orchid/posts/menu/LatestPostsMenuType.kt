package com.eden.orchid.posts.menu

import com.eden.common.util.EdenUtils
import com.eden.orchid.api.OrchidContext
import com.eden.orchid.api.options.annotations.Description
import com.eden.orchid.api.options.annotations.IntDefault
import com.eden.orchid.api.options.annotations.Option
import com.eden.orchid.api.theme.menus.menuItem.OrchidMenuItem
import com.eden.orchid.api.theme.menus.menuItem.OrchidMenuItemImpl
import com.eden.orchid.posts.model.CategoryModel
import com.eden.orchid.posts.model.PostsModel
import javax.inject.Inject

@Description("Latest posts, optionally by category.", name = "Latest Posts")
class LatestPostsMenuType @Inject
constructor(
        context: OrchidContext,
        private val postsModel: PostsModel
) : OrchidMenuItem(context, "latestPosts", 100) {

    @Option @IntDefault(10)
    @Description("The maximum number of posts to include in this menu item.")
    var limit: Int = 10

    @Option
    @Description("Only add latest posts from a specific category.")
    lateinit var category: String

    @Option
    @Description("The title for the root menu item.")
    lateinit var title: String

    override fun getMenuItems(): List<OrchidMenuItemImpl> {
        val items = ArrayList<OrchidMenuItemImpl>()

        val categoryModel: CategoryModel?

        if (!EdenUtils.isEmpty(category) && postsModel.categories.containsKey(category)) {
            categoryModel = postsModel.categories[category]
        } else {
            categoryModel = postsModel.categories[null]
        }

        val latestPosts = postsModel.getRecentPosts(category, limit)
        if(!EdenUtils.isEmpty(latestPosts)) {
            val title = if(!EdenUtils.isEmpty(this.title)) {
                this.title
            }
            else if(!EdenUtils.isEmpty(categoryModel?.title)) {
                "Latest from " + categoryModel?.title
            }
            else {
                "Latest from blog"
            }

            items.add(OrchidMenuItemImpl(context, title, latestPosts))
        }

        return items
    }

}

