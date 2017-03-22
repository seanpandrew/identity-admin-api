import javax.inject.Inject

import filters.{AddEC2InstanceHeader, LogRequestsFilter}
import play.api.http.DefaultHttpFilters

class Filters @Inject() (logRequestsFilter: LogRequestsFilter, addEC2InstanceHeader: AddEC2InstanceHeader)
  extends DefaultHttpFilters(logRequestsFilter, addEC2InstanceHeader)
