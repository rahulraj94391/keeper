package io.github.rahulraj94391.keeper.debug

import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.security.PasswordGenerator
import io.github.rahulraj94391.keeper.security.PasswordOptions

/**
 * Fake logins for trying out the UI (debug builds only; the release source set has an empty
 * version). Every service, username and password is made up; domains use the reserved
 * example.com/.org/.net names.
 */
object SampleCredentials {
    const val AVAILABLE = true
    const val COUNT = 40

    private val services = listOf(
        "Acme Mail", "Bluebird Bank", "Cobalt Cloud", "Driftwood Notes", "Ember Music",
        "Fern Photos", "Granite Git", "Harbor Health", "Indigo Insurance", "Juniper Jobs",
        "Kestrel Kart", "Lumen Learning", "Maple Maps", "Nimbus Drive", "Orchid Office",
        "Pebble Pay", "Quartz Quiz", "Rivet Rides", "Saffron Shop", "Tundra Travel",
        "Umber Utilities", "Velvet Video", "Willow Wiki", "Xenon Exchange", "Yarrow Yoga",
        "Zephyr Zone", "Atlas Airlines", "Birch Books", "Cedar Chat", "Dune Dating",
        "Echo Energy", "Flint Fitness", "Garnet Games", "Hazel Hosting", "Iris ISP",
        "Jade Journal", "Koi Kitchen", "Lark Library", "Moss Mobile", "Nova News",
    )

    private val domains = listOf("example.com", "example.org", "example.net")

    fun all(generator: PasswordGenerator = PasswordGenerator()): List<Credential> =
        services.take(COUNT).mapIndexed { index, service ->
            val number = index + 1
            val slug = service.lowercase().replace(" ", "")
            val domain = domains[index % domains.size]
            Credential(
                id = "sample-%02d".format(number),
                title = service,
                username = if (index % 4 == 3) "user%02d".format(number) else "alex.sample$number@$domain",
                password = generator.generate(PasswordOptions(length = 16 + (index % 5) * 4)),
                url = if (index % 3 == 2) "" else "https://$slug.$domain",
                notes = if (index % 7 == 0) "Sample note for $service. Not a real account." else "",
                favorite = index % 9 == 0,
            )
        }
}
