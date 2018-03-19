package com.eden.orchid.posts.model

import com.caseyjbrooks.clog.Clog
import com.eden.common.util.EdenUtils
import com.eden.orchid.api.OrchidContext
import com.eden.orchid.posts.pages.AuthorPage
import com.eden.orchid.posts.pages.PostPage
import com.eden.orchid.utilities.OrchidUtils
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostsModel
@Inject
constructor(val context: OrchidContext) {

    lateinit var excerptSeparator: String

    var authorPages: List<AuthorPage> = ArrayList()
    var categories: MutableMap<String?, CategoryModel>

    val categoryNames: Set<String?>
        get() = categories.keys

    init {
        this.categories = LinkedHashMap()
    }

    fun initialize(excerptSeparator: String, authorPages: List<AuthorPage>) {
        this.excerptSeparator = excerptSeparator
        this.authorPages = authorPages
        this.categories = LinkedHashMap()
    }

    fun getCategory(category: String?, categoryOptions: JSONObject) : CategoryModel {
        var key: String?
        var path: String

        if(!EdenUtils.isEmpty(category)) {
            val categoryPath = category!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            key = categoryPath.last()
            path = OrchidUtils.normalizePath(categoryPath.joinToString("/"))
        }
        else {
            key = null
            path = ""
        }

        if(!categories.containsKey(category)) {
            val newCategory = CategoryModel(context, key, path)
            newCategory.extractOptions(context, categoryOptions)
            categories[category] = newCategory
        }

        return categories[category]!!
    }

    fun validateCategories(): Boolean {
        var isValid = true

        categories.values.forEach { category ->
            val categoryKeys = category.allCategories

            if(!EdenUtils.isEmpty(categoryKeys)) {
                categoryKeys.forEach { categoryKey ->
                    if(!EdenUtils.isEmpty(categoryKey) && categoryKey != category.key && !categories.containsKey(categoryKey)) {
                        Clog.w("Category ${category.path} is a child of a non-existant parent category $categoryKey")
                        isValid = false
                    }
                }
            }
        }

        return isValid
    }

    fun getAuthorByName(authorName: String): Author? {
        if (!EdenUtils.isEmpty(authorPages)) {
            for (authorPage in authorPages) {
                if (authorPage.author.name.equals(authorName, ignoreCase = true)) {
                    return authorPage.author
                }
            }
        }

        return null
    }

    fun getRecentPosts(category: String?, limitArg: Int): List<PostPage> {
        var limit = if (limitArg > 0) limitArg else 10
        var chosenCategory = ArrayList<PostPage>()

        if (categories.containsKey(category)) {
            chosenCategory.addAll(categories[category]!!.first)
        }
        else {
            chosenCategory = ArrayList()

            for (categoryPosts in categories.values) {
                chosenCategory.addAll(categoryPosts.first)
            }
        }

        chosenCategory.sortWith(PostsModel.postPageComparator)

        val postPages = ArrayList<PostPage>()

        if (limit >= chosenCategory.size) {
            limit = chosenCategory.size
        }

        for (i in 0 until limit) {
            postPages.add(chosenCategory[i])
        }

        return postPages
    }

    companion object {
        var postPageComparator = kotlin.Comparator<PostPage> { o1, o2 ->
            val criteria = arrayOf("year", "month", "day")
            var result = 0

            for (item in criteria) {
                result = 0

                when (item) {
                    "year" -> result = o2.year - o1.year
                    "month" -> result = o2.month - o1.month
                    "day" -> result = o2.day - o1.day
                }

                if (result != 0) {
                    break
                }
            }

            result
        }
    }
}

