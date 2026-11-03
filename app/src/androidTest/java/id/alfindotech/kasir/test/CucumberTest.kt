package id.alfindotech.kasir.test

import io.cucumber.junit.CucumberOptions

@CucumberOptions(
    features = ["features"],
    glue = ["id.alfindotech.kasir.test.steps"],
    plugin = ["pretty"]
)
class CucumberTest